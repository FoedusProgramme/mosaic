const std = @import("std");

pub fn build(b: *std.Build) !void {
	const javaHeaders = b.option([]const u8, "javah", "Path to generated Java headers") orelse {
		std.log.err("javah option is required", .{});
		b.invalid_user_input = true;
		return;
	};

	setupMosaicTarget(b, javaHeaders, .linux, .aarch64, "com/jakewharton/mosaic/tty/jni/aarch64");
	setupMosaicTarget(b, javaHeaders, .linux, .riscv64, "com/jakewharton/mosaic/tty/jni/riscv64");
	setupMosaicTarget(b, javaHeaders, .linux, .s390x, "com/jakewharton/mosaic/tty/jni/s390x");
	setupMosaicTarget(b, javaHeaders, .linux, .x86_64, "com/jakewharton/mosaic/tty/jni/amd64");
	setupMosaicTarget(b, javaHeaders, .macos, .aarch64, "com/jakewharton/mosaic/tty/jni/aarch64");
	setupMosaicTarget(b, javaHeaders, .macos, .x86_64, "com/jakewharton/mosaic/tty/jni/x86_64");
	setupMosaicTarget(b, javaHeaders, .windows, .aarch64, "com/jakewharton/mosaic/tty/jni/aarch64");
	setupMosaicTarget(b, javaHeaders, .windows, .x86_64, "com/jakewharton/mosaic/tty/jni/amd64");
}

fn setupMosaicTarget(b: *std.Build, headers: []const u8, tag: std.Target.Os.Tag, arch: std.Target.Cpu.Arch, dir: []const u8) void {
	const module = b.createModule(.{
		.target = b.resolveTargetQuery(.{
			.cpu_arch = arch,
			.os_tag = tag,
			// We need to explicitly specify gnu for linux, as otherwise it defaults to musl.
			// See https://github.com/ziglang/zig/issues/16624#issuecomment-1801175600.
			.abi = if (tag == .linux) .gnu else null,
		}),
		.optimize = .ReleaseSmall,
		.link_libc = true,
	});

	module.addIncludePath(b.path("src/commonMain/c"));
	module.addIncludePath(b.path(headers));
	module.addIncludePath(b.path("src/jvmMain/include/share"));
	module.addIncludePath(
		switch (tag) {
			.windows => b.path("src/jvmMain/include/windows"),
			else => b.path("src/jvmMain/include/unix"),
		}
	);

	// TODO Tree-walk these two dirs for all C files.
	module.addCSourceFiles(.{
		.files = &.{
			"src/commonMain/c/mosaic-streams-posix.c",
			"src/commonMain/c/mosaic-streams-windows.c",
			"src/commonMain/c/mosaic-test-posix.c",
			"src/commonMain/c/mosaic-test-windows.c",
			"src/commonMain/c/mosaic-tty-posix.c",
			"src/commonMain/c/mosaic-tty-windows.c",
			"src/commonMain/c/mosaic-utils-posix.c",
			"src/commonMain/c/mosaic-utils-windows.c",
			"src/jvmMain/c/com_jakewharton_mosaic_tty_Jni.c",
		},
		.flags = &.{
			"-std=gnu11",
			"-Wall",
			"-Wextra",
			"-Werror",
		},
	});

	const lib = b.addLibrary(.{
		.name = "mosaic",
		.linkage = .dynamic,
		.root_module = module,
	});

	const install = b.addInstallArtifact(lib, .{
		.dest_dir = .{
			.override = .{
				.custom = dir,
			},
		},
		// The Windows builds create a .lib import library which we don't need.
		.implib_dir = .disabled,
	});

	b.getInstallStep().dependOn(&install.step);
}
