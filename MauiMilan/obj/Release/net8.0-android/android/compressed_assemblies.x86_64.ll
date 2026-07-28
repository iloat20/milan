; ModuleID = 'compressed_assemblies.x86_64.ll'
source_filename = "compressed_assemblies.x86_64.ll"
target datalayout = "e-m:e-p270:32:32-p271:32:32-p272:64:64-i64:64-f80:128-n8:16:32:64-S128"
target triple = "x86_64-unknown-linux-android21"

%struct.CompressedAssemblies = type {
	i32, ; uint32_t count
	ptr ; CompressedAssemblyDescriptor descriptors
}

%struct.CompressedAssemblyDescriptor = type {
	i32, ; uint32_t uncompressed_file_size
	i8, ; bool loaded
	ptr ; uint8_t data
}

@compressed_assemblies = dso_local local_unnamed_addr global %struct.CompressedAssemblies {
	i32 28, ; uint32_t count (0x1c)
	ptr @compressed_assembly_descriptors; CompressedAssemblyDescriptor* descriptors
}, align 8

@compressed_assembly_descriptors = internal dso_local global [28 x %struct.CompressedAssemblyDescriptor] [
	%struct.CompressedAssemblyDescriptor {
		i32 129536, ; uint32_t uncompressed_file_size (0x1fa00)
		i8 0, ; bool loaded
		ptr @__compressedAssemblyData_0; uint8_t* data (0x0)
	}, ; 0
	%struct.CompressedAssemblyDescriptor {
		i32 26112, ; uint32_t uncompressed_file_size (0x6600)
		i8 0, ; bool loaded
		ptr @__compressedAssemblyData_1; uint8_t* data (0x0)
	}, ; 1
	%struct.CompressedAssemblyDescriptor {
		i32 18984, ; uint32_t uncompressed_file_size (0x4a28)
		i8 0, ; bool loaded
		ptr @__compressedAssemblyData_2; uint8_t* data (0x0)
	}, ; 2
	%struct.CompressedAssemblyDescriptor {
		i32 212992, ; uint32_t uncompressed_file_size (0x34000)
		i8 0, ; bool loaded
		ptr @__compressedAssemblyData_3; uint8_t* data (0x0)
	}, ; 3
	%struct.CompressedAssemblyDescriptor {
		i32 7168, ; uint32_t uncompressed_file_size (0x1c00)
		i8 0, ; bool loaded
		ptr @__compressedAssemblyData_4; uint8_t* data (0x0)
	}, ; 4
	%struct.CompressedAssemblyDescriptor {
		i32 10752, ; uint32_t uncompressed_file_size (0x2a00)
		i8 0, ; bool loaded
		ptr @__compressedAssemblyData_5; uint8_t* data (0x0)
	}, ; 5
	%struct.CompressedAssemblyDescriptor {
		i32 25088, ; uint32_t uncompressed_file_size (0x6200)
		i8 0, ; bool loaded
		ptr @__compressedAssemblyData_6; uint8_t* data (0x0)
	}, ; 6
	%struct.CompressedAssemblyDescriptor {
		i32 12800, ; uint32_t uncompressed_file_size (0x3200)
		i8 0, ; bool loaded
		ptr @__compressedAssemblyData_7; uint8_t* data (0x0)
	}, ; 7
	%struct.CompressedAssemblyDescriptor {
		i32 56832, ; uint32_t uncompressed_file_size (0xde00)
		i8 0, ; bool loaded
		ptr @__compressedAssemblyData_8; uint8_t* data (0x0)
	}, ; 8
	%struct.CompressedAssemblyDescriptor {
		i32 8192, ; uint32_t uncompressed_file_size (0x2000)
		i8 0, ; bool loaded
		ptr @__compressedAssemblyData_9; uint8_t* data (0x0)
	}, ; 9
	%struct.CompressedAssemblyDescriptor {
		i32 6656, ; uint32_t uncompressed_file_size (0x1a00)
		i8 0, ; bool loaded
		ptr @__compressedAssemblyData_10; uint8_t* data (0x0)
	}, ; 10
	%struct.CompressedAssemblyDescriptor {
		i32 2560, ; uint32_t uncompressed_file_size (0xa00)
		i8 0, ; bool loaded
		ptr @__compressedAssemblyData_11; uint8_t* data (0x0)
	}, ; 11
	%struct.CompressedAssemblyDescriptor {
		i32 17408, ; uint32_t uncompressed_file_size (0x4400)
		i8 0, ; bool loaded
		ptr @__compressedAssemblyData_12; uint8_t* data (0x0)
	}, ; 12
	%struct.CompressedAssemblyDescriptor {
		i32 1427456, ; uint32_t uncompressed_file_size (0x15c800)
		i8 0, ; bool loaded
		ptr @__compressedAssemblyData_13; uint8_t* data (0x0)
	}, ; 13
	%struct.CompressedAssemblyDescriptor {
		i32 31232, ; uint32_t uncompressed_file_size (0x7a00)
		i8 0, ; bool loaded
		ptr @__compressedAssemblyData_14; uint8_t* data (0x0)
	}, ; 14
	%struct.CompressedAssemblyDescriptor {
		i32 313856, ; uint32_t uncompressed_file_size (0x4ca00)
		i8 0, ; bool loaded
		ptr @__compressedAssemblyData_15; uint8_t* data (0x0)
	}, ; 15
	%struct.CompressedAssemblyDescriptor {
		i32 16896, ; uint32_t uncompressed_file_size (0x4200)
		i8 0, ; bool loaded
		ptr @__compressedAssemblyData_16; uint8_t* data (0x0)
	}, ; 16
	%struct.CompressedAssemblyDescriptor {
		i32 1412096, ; uint32_t uncompressed_file_size (0x158c00)
		i8 0, ; bool loaded
		ptr @__compressedAssemblyData_17; uint8_t* data (0x0)
	}, ; 17
	%struct.CompressedAssemblyDescriptor {
		i32 28160, ; uint32_t uncompressed_file_size (0x6e00)
		i8 0, ; bool loaded
		ptr @__compressedAssemblyData_18; uint8_t* data (0x0)
	}, ; 18
	%struct.CompressedAssemblyDescriptor {
		i32 313856, ; uint32_t uncompressed_file_size (0x4ca00)
		i8 0, ; bool loaded
		ptr @__compressedAssemblyData_19; uint8_t* data (0x0)
	}, ; 19
	%struct.CompressedAssemblyDescriptor {
		i32 16896, ; uint32_t uncompressed_file_size (0x4200)
		i8 0, ; bool loaded
		ptr @__compressedAssemblyData_20; uint8_t* data (0x0)
	}, ; 20
	%struct.CompressedAssemblyDescriptor {
		i32 1411584, ; uint32_t uncompressed_file_size (0x158a00)
		i8 0, ; bool loaded
		ptr @__compressedAssemblyData_21; uint8_t* data (0x0)
	}, ; 21
	%struct.CompressedAssemblyDescriptor {
		i32 28160, ; uint32_t uncompressed_file_size (0x6e00)
		i8 0, ; bool loaded
		ptr @__compressedAssemblyData_22; uint8_t* data (0x0)
	}, ; 22
	%struct.CompressedAssemblyDescriptor {
		i32 313856, ; uint32_t uncompressed_file_size (0x4ca00)
		i8 0, ; bool loaded
		ptr @__compressedAssemblyData_23; uint8_t* data (0x0)
	}, ; 23
	%struct.CompressedAssemblyDescriptor {
		i32 17408, ; uint32_t uncompressed_file_size (0x4400)
		i8 0, ; bool loaded
		ptr @__compressedAssemblyData_24; uint8_t* data (0x0)
	}, ; 24
	%struct.CompressedAssemblyDescriptor {
		i32 1473536, ; uint32_t uncompressed_file_size (0x167c00)
		i8 0, ; bool loaded
		ptr @__compressedAssemblyData_25; uint8_t* data (0x0)
	}, ; 25
	%struct.CompressedAssemblyDescriptor {
		i32 30208, ; uint32_t uncompressed_file_size (0x7600)
		i8 0, ; bool loaded
		ptr @__compressedAssemblyData_26; uint8_t* data (0x0)
	}, ; 26
	%struct.CompressedAssemblyDescriptor {
		i32 313856, ; uint32_t uncompressed_file_size (0x4ca00)
		i8 0, ; bool loaded
		ptr @__compressedAssemblyData_27; uint8_t* data (0x0)
	} ; 27
], align 16

@__compressedAssemblyData_0 = internal dso_local global [129536 x i8] zeroinitializer, align 16
@__compressedAssemblyData_1 = internal dso_local global [26112 x i8] zeroinitializer, align 16
@__compressedAssemblyData_2 = internal dso_local global [18984 x i8] zeroinitializer, align 16
@__compressedAssemblyData_3 = internal dso_local global [212992 x i8] zeroinitializer, align 16
@__compressedAssemblyData_4 = internal dso_local global [7168 x i8] zeroinitializer, align 16
@__compressedAssemblyData_5 = internal dso_local global [10752 x i8] zeroinitializer, align 16
@__compressedAssemblyData_6 = internal dso_local global [25088 x i8] zeroinitializer, align 16
@__compressedAssemblyData_7 = internal dso_local global [12800 x i8] zeroinitializer, align 16
@__compressedAssemblyData_8 = internal dso_local global [56832 x i8] zeroinitializer, align 16
@__compressedAssemblyData_9 = internal dso_local global [8192 x i8] zeroinitializer, align 16
@__compressedAssemblyData_10 = internal dso_local global [6656 x i8] zeroinitializer, align 16
@__compressedAssemblyData_11 = internal dso_local global [2560 x i8] zeroinitializer, align 16
@__compressedAssemblyData_12 = internal dso_local global [17408 x i8] zeroinitializer, align 16
@__compressedAssemblyData_13 = internal dso_local global [1427456 x i8] zeroinitializer, align 16
@__compressedAssemblyData_14 = internal dso_local global [31232 x i8] zeroinitializer, align 16
@__compressedAssemblyData_15 = internal dso_local global [313856 x i8] zeroinitializer, align 16
@__compressedAssemblyData_16 = internal dso_local global [16896 x i8] zeroinitializer, align 16
@__compressedAssemblyData_17 = internal dso_local global [1412096 x i8] zeroinitializer, align 16
@__compressedAssemblyData_18 = internal dso_local global [28160 x i8] zeroinitializer, align 16
@__compressedAssemblyData_19 = internal dso_local global [313856 x i8] zeroinitializer, align 16
@__compressedAssemblyData_20 = internal dso_local global [16896 x i8] zeroinitializer, align 16
@__compressedAssemblyData_21 = internal dso_local global [1411584 x i8] zeroinitializer, align 16
@__compressedAssemblyData_22 = internal dso_local global [28160 x i8] zeroinitializer, align 16
@__compressedAssemblyData_23 = internal dso_local global [313856 x i8] zeroinitializer, align 16
@__compressedAssemblyData_24 = internal dso_local global [17408 x i8] zeroinitializer, align 16
@__compressedAssemblyData_25 = internal dso_local global [1473536 x i8] zeroinitializer, align 16
@__compressedAssemblyData_26 = internal dso_local global [30208 x i8] zeroinitializer, align 16
@__compressedAssemblyData_27 = internal dso_local global [313856 x i8] zeroinitializer, align 16

; Metadata
!llvm.module.flags = !{!0, !1}
!0 = !{i32 1, !"wchar_size", i32 4}
!1 = !{i32 7, !"PIC Level", i32 2}
!llvm.ident = !{!2}
!2 = !{!"Xamarin.Android remotes/origin/release/8.0.4xx @ 82d8938cf80f6d5fa6c28529ddfbdb753d805ab4"}
!3 = !{!4, !4, i64 0}
!4 = !{!"any pointer", !5, i64 0}
!5 = !{!"omnipotent char", !6, i64 0}
!6 = !{!"Simple C++ TBAA"}
