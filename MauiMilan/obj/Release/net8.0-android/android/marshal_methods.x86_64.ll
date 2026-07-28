; ModuleID = 'marshal_methods.x86_64.ll'
source_filename = "marshal_methods.x86_64.ll"
target datalayout = "e-m:e-p270:32:32-p271:32:32-p272:64:64-i64:64-f80:128-n8:16:32:64-S128"
target triple = "x86_64-unknown-linux-android21"

%struct.MarshalMethodName = type {
	i64, ; uint64_t id
	ptr ; char* name
}

%struct.MarshalMethodsManagedClass = type {
	i32, ; uint32_t token
	ptr ; MonoClass klass
}

@assembly_image_cache = dso_local local_unnamed_addr global [16 x ptr] zeroinitializer, align 16

; Each entry maps hash of an assembly name to an index into the `assembly_image_cache` array
@assembly_image_cache_hashes = dso_local local_unnamed_addr constant [32 x i64] [
	i64 120698629574877762, ; 0: Mono.Android => 0x1accec39cafe242 => 15
	i64 1513467482682125403, ; 1: Mono.Android.Runtime => 0x1500eaa8245f6c5b => 14
	i64 1743969030606105336, ; 2: System.Memory.dll => 0x1833d297e88f2af8 => 6
	i64 1767386781656293639, ; 3: System.Private.Uri.dll => 0x188704e9f5582107 => 7
	i64 2287834202362508563, ; 4: System.Collections.Concurrent => 0x1fc00515e8ce7513 => 2
	i64 2335503487726329082, ; 5: System.Text.Encodings.Web => 0x2069600c4d9d1cfa => 10
	i64 2497223385847772520, ; 6: System.Runtime => 0x22a7eb7046413568 => 9
	i64 3551103847008531295, ; 7: System.Private.CoreLib.dll => 0x31480e226177735f => 12
	i64 3571415421602489686, ; 8: System.Runtime.dll => 0x319037675df7e556 => 9
	i64 3966267475168208030, ; 9: System.Memory => 0x370b03412596249e => 6
	i64 5527451471949963832, ; 10: MauiMilan.dll => 0x4cb573d3fac9a238 => 1
	i64 6222399776351216807, ; 11: System.Text.Json.dll => 0x565a67a0ffe264a7 => 11
	i64 6357457916754632952, ; 12: _Microsoft.Android.Resource.Designer => 0x583a3a4ac2a7a0f8 => 0
	i64 7270811800166795866, ; 13: System.Linq => 0x64e71ccf51a90a5a => 5
	i64 7714652370974252055, ; 14: System.Private.CoreLib => 0x6b0ff375198b9c17 => 12
	i64 8064050204834738623, ; 15: System.Collections.dll => 0x6fe942efa61731bf => 3
	i64 8167236081217502503, ; 16: Java.Interop.dll => 0x7157d9f1a9b8fd27 => 13
	i64 8185542183669246576, ; 17: System.Collections => 0x7198e33f4794aa70 => 3
	i64 8563666267364444763, ; 18: System.Private.Uri => 0x76d841191140ca5b => 7
	i64 8626175481042262068, ; 19: Java.Interop => 0x77b654e585b55834 => 13
	i64 9808709177481450983, ; 20: Mono.Android.dll => 0x881f890734e555e7 => 15
	i64 11485890710487134646, ; 21: System.Runtime.InteropServices => 0x9f6614bf0f8b71b6 => 8
	i64 12145679461940342714, ; 22: System.Text.Json => 0xa88e1f1ebcb62fba => 11
	i64 12475113361194491050, ; 23: _Microsoft.Android.Resource.Designer.dll => 0xad2081818aba1caa => 0
	i64 12877985600895856702, ; 24: MauiMilan => 0xb2b7cbac6bfe303e => 1
	i64 13343850469010654401, ; 25: Mono.Android.Runtime.dll => 0xb92ee14d854f44c1 => 14
	i64 13881769479078963060, ; 26: System.Console.dll => 0xc0a5f3cade5c6774 => 4
	i64 14551742072151931844, ; 27: System.Text.Encodings.Web.dll => 0xc9f22c50f1b8fbc4 => 10
	i64 15133485256822086103, ; 28: System.Linq.dll => 0xd204f0a9127dd9d7 => 5
	i64 15527772828719725935, ; 29: System.Console => 0xd77dbb1e38cd3d6f => 4
	i64 17712670374920797664, ; 30: System.Runtime.InteropServices.dll => 0xf5d00bdc38bd3de0 => 8
	i64 18245806341561545090 ; 31: System.Collections.Concurrent.dll => 0xfd3620327d587182 => 2
], align 16

@assembly_image_cache_indices = dso_local local_unnamed_addr constant [32 x i32] [
	i32 15, ; 0
	i32 14, ; 1
	i32 6, ; 2
	i32 7, ; 3
	i32 2, ; 4
	i32 10, ; 5
	i32 9, ; 6
	i32 12, ; 7
	i32 9, ; 8
	i32 6, ; 9
	i32 1, ; 10
	i32 11, ; 11
	i32 0, ; 12
	i32 5, ; 13
	i32 12, ; 14
	i32 3, ; 15
	i32 13, ; 16
	i32 3, ; 17
	i32 7, ; 18
	i32 13, ; 19
	i32 15, ; 20
	i32 8, ; 21
	i32 11, ; 22
	i32 0, ; 23
	i32 1, ; 24
	i32 14, ; 25
	i32 4, ; 26
	i32 10, ; 27
	i32 5, ; 28
	i32 4, ; 29
	i32 8, ; 30
	i32 2 ; 31
], align 16

@marshal_methods_number_of_classes = dso_local local_unnamed_addr constant i32 0, align 4

@marshal_methods_class_cache = dso_local local_unnamed_addr global [0 x %struct.MarshalMethodsManagedClass] zeroinitializer, align 8

; Names of classes in which marshal methods reside
@mm_class_names = dso_local local_unnamed_addr constant [0 x ptr] zeroinitializer, align 8

@mm_method_names = dso_local local_unnamed_addr constant [1 x %struct.MarshalMethodName] [
	%struct.MarshalMethodName {
		i64 0, ; id 0x0; name: 
		ptr @.MarshalMethodName.0_name; char* name
	} ; 0
], align 8

; get_function_pointer (uint32_t mono_image_index, uint32_t class_index, uint32_t method_token, void*& target_ptr)
@get_function_pointer = internal dso_local unnamed_addr global ptr null, align 8

; Functions

; Function attributes: "min-legal-vector-width"="0" mustprogress "no-trapping-math"="true" nofree norecurse nosync nounwind "stack-protector-buffer-size"="8" uwtable willreturn
define void @xamarin_app_init(ptr nocapture noundef readnone %env, ptr noundef %fn) local_unnamed_addr #0
{
	%fnIsNull = icmp eq ptr %fn, null
	br i1 %fnIsNull, label %1, label %2

1: ; preds = %0
	%putsResult = call noundef i32 @puts(ptr @.str.0)
	call void @abort()
	unreachable 

2: ; preds = %1, %0
	store ptr %fn, ptr @get_function_pointer, align 8, !tbaa !3
	ret void
}

; Strings
@.str.0 = private unnamed_addr constant [40 x i8] c"get_function_pointer MUST be specified\0A\00", align 16

;MarshalMethodName
@.MarshalMethodName.0_name = private unnamed_addr constant [1 x i8] c"\00", align 1

; External functions

; Function attributes: "no-trapping-math"="true" noreturn nounwind "stack-protector-buffer-size"="8"
declare void @abort() local_unnamed_addr #2

; Function attributes: nofree nounwind
declare noundef i32 @puts(ptr noundef) local_unnamed_addr #1
attributes #0 = { "min-legal-vector-width"="0" mustprogress "no-trapping-math"="true" nofree norecurse nosync nounwind "stack-protector-buffer-size"="8" "target-cpu"="x86-64" "target-features"="+crc32,+cx16,+cx8,+fxsr,+mmx,+popcnt,+sse,+sse2,+sse3,+sse4.1,+sse4.2,+ssse3,+x87" "tune-cpu"="generic" uwtable willreturn }
attributes #1 = { nofree nounwind }
attributes #2 = { "no-trapping-math"="true" noreturn nounwind "stack-protector-buffer-size"="8" "target-cpu"="x86-64" "target-features"="+crc32,+cx16,+cx8,+fxsr,+mmx,+popcnt,+sse,+sse2,+sse3,+sse4.1,+sse4.2,+ssse3,+x87" "tune-cpu"="generic" }

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
