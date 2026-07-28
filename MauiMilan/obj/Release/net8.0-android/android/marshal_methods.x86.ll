; ModuleID = 'marshal_methods.x86.ll'
source_filename = "marshal_methods.x86.ll"
target datalayout = "e-m:e-p:32:32-p270:32:32-p271:32:32-p272:64:64-f64:32:64-f80:32-n8:16:32-S128"
target triple = "i686-unknown-linux-android21"

%struct.MarshalMethodName = type {
	i64, ; uint64_t id
	ptr ; char* name
}

%struct.MarshalMethodsManagedClass = type {
	i32, ; uint32_t token
	ptr ; MonoClass klass
}

@assembly_image_cache = dso_local local_unnamed_addr global [16 x ptr] zeroinitializer, align 4

; Each entry maps hash of an assembly name to an index into the `assembly_image_cache` array
@assembly_image_cache_hashes = dso_local local_unnamed_addr constant [32 x i32] [
	i32 89650930, ; 0: MauiMilan => 0x557f6f2 => 1
	i32 117431740, ; 1: System.Runtime.InteropServices => 0x6ffddbc => 8
	i32 385762202, ; 2: System.Memory.dll => 0x16fe439a => 6
	i32 395744057, ; 3: _Microsoft.Android.Resource.Designer => 0x17969339 => 0
	i32 442565967, ; 4: System.Collections => 0x1a61054f => 3
	i32 662205335, ; 5: System.Text.Encodings.Web.dll => 0x27787397 => 10
	i32 672442732, ; 6: System.Collections.Concurrent => 0x2814a96c => 2
	i32 823281589, ; 7: System.Private.Uri.dll => 0x311247b5 => 7
	i32 992768348, ; 8: System.Collections.dll => 0x3b2c715c => 3
	i32 1052509064, ; 9: MauiMilan.dll => 0x3ebc0388 => 1
	i32 1324164729, ; 10: System.Linq => 0x4eed2679 => 5
	i32 1657153582, ; 11: System.Runtime => 0x62c6282e => 9
	i32 1780572499, ; 12: Mono.Android.Runtime.dll => 0x6a216153 => 14
	i32 2079903147, ; 13: System.Runtime.dll => 0x7bf8cdab => 9
	i32 2127167465, ; 14: System.Console => 0x7ec9ffe9 => 4
	i32 2305521784, ; 15: System.Private.CoreLib.dll => 0x896b7878 => 12
	i32 2435356389, ; 16: System.Console.dll => 0x912896e5 => 4
	i32 2475788418, ; 17: Java.Interop.dll => 0x93918882 => 13
	i32 2570120770, ; 18: System.Text.Encodings.Web => 0x9930ee42 => 10
	i32 2909740682, ; 19: System.Private.CoreLib => 0xad6f1e8a => 12
	i32 3038032645, ; 20: _Microsoft.Android.Resource.Designer.dll => 0xb514b305 => 0
	i32 3059408633, ; 21: Mono.Android.Runtime => 0xb65adef9 => 14
	i32 3358260929, ; 22: System.Text.Json => 0xc82afec1 => 11
	i32 3366347497, ; 23: Java.Interop => 0xc8a662e9 => 13
	i32 3476120550, ; 24: Mono.Android => 0xcf3163e6 => 15
	i32 3485117614, ; 25: System.Text.Json.dll => 0xcfbaacae => 11
	i32 3608519521, ; 26: System.Linq.dll => 0xd715a361 => 5
	i32 3672681054, ; 27: Mono.Android.dll => 0xdae8aa5e => 15
	i32 3849253459, ; 28: System.Runtime.InteropServices.dll => 0xe56ef253 => 8
	i32 3896106733, ; 29: System.Collections.Concurrent.dll => 0xe839deed => 2
	i32 4025784931, ; 30: System.Memory => 0xeff49a63 => 6
	i32 4100113165 ; 31: System.Private.Uri => 0xf462c30d => 7
], align 4

@assembly_image_cache_indices = dso_local local_unnamed_addr constant [32 x i32] [
	i32 1, ; 0
	i32 8, ; 1
	i32 6, ; 2
	i32 0, ; 3
	i32 3, ; 4
	i32 10, ; 5
	i32 2, ; 6
	i32 7, ; 7
	i32 3, ; 8
	i32 1, ; 9
	i32 5, ; 10
	i32 9, ; 11
	i32 14, ; 12
	i32 9, ; 13
	i32 4, ; 14
	i32 12, ; 15
	i32 4, ; 16
	i32 13, ; 17
	i32 10, ; 18
	i32 12, ; 19
	i32 0, ; 20
	i32 14, ; 21
	i32 11, ; 22
	i32 13, ; 23
	i32 15, ; 24
	i32 11, ; 25
	i32 5, ; 26
	i32 15, ; 27
	i32 8, ; 28
	i32 2, ; 29
	i32 6, ; 30
	i32 7 ; 31
], align 4

@marshal_methods_number_of_classes = dso_local local_unnamed_addr constant i32 0, align 4

@marshal_methods_class_cache = dso_local local_unnamed_addr global [0 x %struct.MarshalMethodsManagedClass] zeroinitializer, align 4

; Names of classes in which marshal methods reside
@mm_class_names = dso_local local_unnamed_addr constant [0 x ptr] zeroinitializer, align 4

@mm_method_names = dso_local local_unnamed_addr constant [1 x %struct.MarshalMethodName] [
	%struct.MarshalMethodName {
		i64 0, ; id 0x0; name: 
		ptr @.MarshalMethodName.0_name; char* name
	} ; 0
], align 8

; get_function_pointer (uint32_t mono_image_index, uint32_t class_index, uint32_t method_token, void*& target_ptr)
@get_function_pointer = internal dso_local unnamed_addr global ptr null, align 4

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
	store ptr %fn, ptr @get_function_pointer, align 4, !tbaa !3
	ret void
}

; Strings
@.str.0 = private unnamed_addr constant [40 x i8] c"get_function_pointer MUST be specified\0A\00", align 1

;MarshalMethodName
@.MarshalMethodName.0_name = private unnamed_addr constant [1 x i8] c"\00", align 1

; External functions

; Function attributes: "no-trapping-math"="true" noreturn nounwind "stack-protector-buffer-size"="8"
declare void @abort() local_unnamed_addr #2

; Function attributes: nofree nounwind
declare noundef i32 @puts(ptr noundef) local_unnamed_addr #1
attributes #0 = { "min-legal-vector-width"="0" mustprogress "no-trapping-math"="true" nofree norecurse nosync nounwind "stack-protector-buffer-size"="8" "stackrealign" "target-cpu"="i686" "target-features"="+cx8,+mmx,+sse,+sse2,+sse3,+ssse3,+x87" "tune-cpu"="generic" uwtable willreturn }
attributes #1 = { nofree nounwind }
attributes #2 = { "no-trapping-math"="true" noreturn nounwind "stack-protector-buffer-size"="8" "stackrealign" "target-cpu"="i686" "target-features"="+cx8,+mmx,+sse,+sse2,+sse3,+ssse3,+x87" "tune-cpu"="generic" }

; Metadata
!llvm.module.flags = !{!0, !1, !7}
!0 = !{i32 1, !"wchar_size", i32 4}
!1 = !{i32 7, !"PIC Level", i32 2}
!llvm.ident = !{!2}
!2 = !{!"Xamarin.Android remotes/origin/release/8.0.4xx @ 82d8938cf80f6d5fa6c28529ddfbdb753d805ab4"}
!3 = !{!4, !4, i64 0}
!4 = !{!"any pointer", !5, i64 0}
!5 = !{!"omnipotent char", !6, i64 0}
!6 = !{!"Simple C++ TBAA"}
!7 = !{i32 1, !"NumRegisterParameters", i32 0}
