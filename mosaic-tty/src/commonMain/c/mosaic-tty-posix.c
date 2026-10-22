#if defined(__APPLE__) || defined(__linux__)

#include "mosaic-tty-posix.h"
#include "mosaic-utils-posix.h"

#include <errno.h>
#include <fcntl.h>
#include <signal.h>
#include <stdatomic.h>
#include <stdlib.h>
#include <sys/ioctl.h>
#include <sys/select.h>
#include <termios.h>
#include <time.h>
#include <unistd.h>

static _Atomic(MosaicTty *) globalTty;

MosaicTtyInitResult mosaic_tty_init_with_fd(int fd) {
	MosaicTtyInitResult result = {};

	MosaicTtyImpl *tty = calloc(1, sizeof(MosaicTtyImpl));
	if (unlikely(tty == NULL)) {
		// result.tty is set to 0 which will trigger OOM.
		goto ret;
	}

	int interruptPipe[2];
	if (unlikely(pipe(interruptPipe)) != 0) {
		result.error = errno;
		goto err;
	}
	int resizePipe[2];
	if (unlikely(pipe(resizePipe)) != 0) {
		result.error = errno;
		close(interruptPipe[0]);
		close(interruptPipe[1]);
		goto err;
	}
	// Neither end may block: the writer is used from a signal handler, and the reader is drained.
	fcntl(resizePipe[0], F_SETFL, fcntl(resizePipe[0], F_GETFL) | O_NONBLOCK);
	fcntl(resizePipe[1], F_SETFL, fcntl(resizePipe[1], F_GETFL) | O_NONBLOCK);

	tty->fd = fd;
	tty->interrupt_fd_reader = interruptPipe[0];
	tty->interrupt_fd_writer = interruptPipe[1];
	tty->resize_fd_reader = resizePipe[0];
	tty->resize_fd_writer = resizePipe[1];

	result.tty = tty;

	MosaicTty *expected = NULL;
	if (likely(tty) && !atomic_compare_exchange_strong(&globalTty, &expected, tty)) {
		// We initialized an instance but there already was a global instance.
		result.tty = NULL;
		result.error = mosaic_tty_free(tty);
		result.already_bound = true;
	}

	ret:
	return result;

	err:
	free(tty);
	goto ret;
}

MosaicTtyInitResult mosaic_tty_init(void) {
	int fd = open("/dev/tty", O_RDWR);
	if (likely(fd != -1)) {
		return mosaic_tty_init_with_fd(fd);
	}

	MosaicTtyInitResult result = {};
	result.error = errno;
	result.no_tty = true;
	return result;
}

void mosaic_tty_set_callback(MosaicTty *tty, MosaicTtyCallback *callback) {
	tty->callback = callback;
}

static void mosaic_tty_dispatch_resize(MosaicTty *tty) {
	struct winsize size;
	if (ioctl(tty->fd, TIOCGWINSZ, &size) != -1) {
		MosaicTtyCallback *callback = tty->callback;
		if (likely(callback)) {
			callback->onResize(callback->opaque, size.ws_col, size.ws_row, size.ws_xpixel, size.ws_ypixel);
		}
	}
}

static MosaicIoResult mosaic_tty_read_internal(
	MosaicTty *tty,
	uint8_t *buffer,
	int count,
	struct timeval *timeout
) {
	MosaicIoResult result = {};

	while (true) {
		fd_set fds;
		FD_ZERO(&fds);
		FD_SET(tty->fd, &fds);
		FD_SET(tty->interrupt_fd_reader, &fds);
		FD_SET(tty->resize_fd_reader, &fds);

		int nfds = tty->fd;
		if (tty->interrupt_fd_reader > nfds) nfds = tty->interrupt_fd_reader;
		if (tty->resize_fd_reader > nfds) nfds = tty->resize_fd_reader;

		int ready = select(nfds + 1, &fds, NULL, NULL, timeout);
		if (unlikely(ready < 0)) {
			if (errno == EINTR) {
				// Most likely SIGWINCH, whose notification is picked up on the next iteration.
				continue;
			}
			result.error = errno;
			return result;
		}

		if (unlikely(FD_ISSET(tty->resize_fd_reader, &fds) != 0)) {
			// Coalesce all pending notifications into a single callback with the latest size.
			uint8_t drain[16];
			while (read(tty->resize_fd_reader, drain, sizeof(drain)) > 0) {}
			mosaic_tty_dispatch_resize(tty);
			if (FD_ISSET(tty->fd, &fds) == 0 && FD_ISSET(tty->interrupt_fd_reader, &fds) == 0) {
				// Keep waiting for input rather than returning 0, which would signal an interrupt.
				continue;
			}
		}

		if (likely(FD_ISSET(tty->fd, &fds) != 0)) {
			int c = read(tty->fd, buffer, count);
			if (likely(c > 0)) {
				result.count = c;
			} else if (c == 0) {
				result.count = -1; // EOF
			} else {
				result.error = errno;
			}
		} else if (unlikely(FD_ISSET(tty->interrupt_fd_reader, &fds) != 0)) {
			// Consume the single notification byte to clear the ready state for the next call.
			uint8_t space;
			if (unlikely(read(tty->interrupt_fd_reader, &space, 1) < 0)) {
				result.error = errno;
			}
		}
		// Otherwise if the interrupt pipe was selected or we timed out, return a count of 0.
		return result;
	}
}

MosaicIoResult mosaic_tty_read(MosaicTty *tty, uint8_t *buffer, int count) {
	return mosaic_tty_read_internal(tty, buffer, count, NULL);
}

MosaicIoResult mosaic_tty_read_with_timeout(
	MosaicTty *tty,
	uint8_t *buffer,
	int count,
	int timeoutMillis
) {
	struct timeval timeout;
	timeout.tv_sec = 0;
	timeout.tv_usec = timeoutMillis * 1000;

	return mosaic_tty_read_internal(tty, buffer, count, &timeout);
}

uint32_t mosaic_tty_interrupt_read(MosaicTty *tty) {
	uint8_t space = ' ';
	MosaicIoResult result = mosaic_utils_write(tty->interrupt_fd_writer, &space, 1);
	return result.error;
}

MosaicIoResult mosaic_tty_write(MosaicTty *tty, uint8_t *buffer, int count) {
	return mosaic_utils_write(tty->fd, buffer, count);
}

static void mosaic_tty_sigwinch_handler(int value UNUSED) {
	// Signal handlers may run on any thread and only call async-signal-safe functions, so only wake
	// the reading thread which then queries the size and invokes the callback.
	MosaicTty *tty = atomic_load(&globalTty);
	if (likely(tty)) {
		int savedErrno = errno;
		uint8_t notification = 1;
		// A full pipe already has a pending notification, so a failed write can be ignored.
		ssize_t ignored = write(tty->resize_fd_writer, &notification, 1);
		(void) ignored;
		errno = savedErrno;
	}
}

uint32_t mosaic_tty_enable_raw_mode(MosaicTty *tty) {
	uint32_t result = 0;

	if (unlikely(tty->saved)) {
		goto ret; // Already enabled!
	}

	struct termios *saved = calloc(1, sizeof(struct termios));
	if (unlikely(saved == NULL)) {
		result = ENOMEM;
		goto ret;
	}

	if (unlikely(tcgetattr(tty->fd, saved) != 0)) {
		result = errno;
		goto err;
	}

	struct termios current = (*saved);

	// Flags as defined by "Raw mode" section of https://linux.die.net/man/3/termios
	current.c_iflag &= ~(BRKINT | ICRNL | IGNBRK | IGNCR | INLCR | ISTRIP | IXON | PARMRK);
	current.c_oflag &= ~(OPOST);
	// Setting ECHONL should be useless here, but it is what is documented for cfmakeraw.
	current.c_lflag &= ~(ECHO | ECHONL | ICANON | IEXTEN | ISIG);
	current.c_cflag &= ~(CSIZE | PARENB);
	current.c_cflag |= (CS8);

	current.c_cc[VMIN] = 1;
	current.c_cc[VTIME] = 0;

	if (unlikely(tcsetattr(tty->fd, TCSAFLUSH, &current) != 0)) {
		result = errno;
		// Try to restore the saved config.
		tcsetattr(tty->fd, TCSAFLUSH, saved);
		goto err;
	}

	tty->saved = saved;

	ret:
	return result;

	err:
	free(saved);
	goto ret;
}

uint32_t mosaic_tty_enable_window_resize_events(MosaicTty *tty) {
	if (tty->sigwinch) {
		return 0; // Already installed.
	}

	struct sigaction action;
	action.sa_handler = mosaic_tty_sigwinch_handler;
	sigemptyset(&action.sa_mask);
	action.sa_flags = 0;

	if (likely(sigaction(SIGWINCH, &action, NULL) == 0)) {
		tty->sigwinch = true;
		return 0;
	}
	return errno;
}

MosaicTtyTerminalSizeResult mosaic_tty_current_terminal_size(MosaicTty *tty) {
	MosaicTtyTerminalSizeResult result = {};

	struct winsize size;
	if (ioctl(tty->fd, TIOCGWINSZ, &size) != -1) {
		result.columns = size.ws_col;
		result.rows = size.ws_row;
		result.width = size.ws_xpixel;
		result.height = size.ws_ypixel;
	} else {
		result.error = errno;
	}

	return result;
}

uint32_t mosaic_tty_reset(MosaicTty *tty) {
	uint32_t result = 0;

	if (tty->sigwinch) {
		if (unlikely(signal(SIGWINCH, SIG_DFL) == SIG_ERR)) {
			result = errno;
		}
		tty->sigwinch = false;
	}

	if (tty->saved) {
		if (unlikely(tcsetattr(tty->fd, TCSAFLUSH, tty->saved) && result == 0)) {
			result = errno;
		}
		free(tty->saved);
		tty->saved = NULL;
	}

	return result;
}

uint32_t mosaic_tty_free(MosaicTty *tty) {
	uint32_t result = 0;

	uint32_t resetResult = mosaic_tty_reset(tty);
	if (resetResult != 0) {
		result = resetResult;
	}

	if (unlikely(close(tty->fd) != 0 && result == 0)) {
		result = errno;
	}

	if (unlikely(close(tty->interrupt_fd_reader) != 0 && result == 0)) {
		result = errno;
	}
	if (unlikely(close(tty->interrupt_fd_writer) != 0 && result == 0)) {
		result = errno;
	}
	if (unlikely(close(tty->resize_fd_reader) != 0 && result == 0)) {
		result = errno;
	}
	if (unlikely(close(tty->resize_fd_writer) != 0 && result == 0)) {
		result = errno;
	}

	atomic_store(&globalTty, NULL);
	free(tty);
	return result;
}

#endif
