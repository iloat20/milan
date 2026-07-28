; ModuleID = 'typemaps.armeabi-v7a.ll'
source_filename = "typemaps.armeabi-v7a.ll"
target datalayout = "e-m:e-p:32:32-Fi8-i64:64-v128:64:128-a:0:32-n32-S64"
target triple = "armv7-unknown-linux-android21"

%struct.TypeMapJava = type {
	i32, ; uint32_t module_index
	i32, ; uint32_t type_token_id
	i32 ; uint32_t java_name_index
}

%struct.TypeMapModule = type {
	[16 x i8], ; uint8_t module_uuid[16]
	i32, ; uint32_t entry_count
	i32, ; uint32_t duplicate_count
	ptr, ; TypeMapModuleEntry map
	ptr, ; TypeMapModuleEntry duplicate_map
	ptr, ; char* assembly_name
	ptr, ; MonoImage image
	i32, ; uint32_t java_name_width
	ptr ; uint8_t java_map
}

%struct.TypeMapModuleEntry = type {
	i32, ; uint32_t type_token_id
	i32 ; uint32_t java_map_index
}

@map_module_count = dso_local local_unnamed_addr constant i32 2, align 4

@java_type_count = dso_local local_unnamed_addr constant i32 52, align 4

; Managed modules map
@map_modules = dso_local local_unnamed_addr global [2 x %struct.TypeMapModule] [
	%struct.TypeMapModule {
		[16 x i8] c"\BB\B6C_!\1E\D9A\B4v?\9F\E3\85\CD\DB", ; module_uuid: 5f43b6bb-1e21-41d9-b476-3f9fe385cddb
		i32 1, ; uint32_t entry_count (0x1)
		i32 0, ; uint32_t duplicate_count (0x0)
		ptr @module0_managed_to_java, ; TypeMapModuleEntry* map
		ptr null, ; TypeMapModuleEntry* duplicate_map
		ptr @.TypeMapModule.0_assembly_name, ; assembly_name: MauiMilan
		ptr null, ; MonoImage* image
		i32 0, ; uint32_t java_name_width (0x0)
		ptr null; uint8_t* java_map (0x0)
	}, ; 0
	%struct.TypeMapModule {
		[16 x i8] c"\CC]b)\BA\C6\02G\BE\04\8D\A2\E2\0C\85:", ; module_uuid: 29625dcc-c6ba-4702-be04-8da2e20c853a
		i32 51, ; uint32_t entry_count (0x33)
		i32 13, ; uint32_t duplicate_count (0xd)
		ptr @module1_managed_to_java, ; TypeMapModuleEntry* map
		ptr @module1_managed_to_java_duplicates, ; TypeMapModuleEntry* duplicate_map
		ptr @.TypeMapModule.1_assembly_name, ; assembly_name: Mono.Android
		ptr null, ; MonoImage* image
		i32 0, ; uint32_t java_name_width (0x0)
		ptr null; uint8_t* java_map (0x0)
	} ; 1
], align 4

; Java types name hashes
@map_java_hashes = dso_local local_unnamed_addr constant [52 x i32] [
	i32 12341354, ; 0: 0xbc506a => java/lang/Object
	i32 74282880, ; 1: 0x46d7780 => android/view/ViewGroup
	i32 366534601, ; 2: 0x15d8dfc9 => android/view/ViewGroup$LayoutParams
	i32 393371378, ; 3: 0x17725ef2 => mono/java/lang/RunnableImplementor
	i32 531198748, ; 4: 0x1fa9731c => mono/android/runtime/OutputStreamAdapter
	i32 581097368, ; 5: 0x22a2d798 => java/nio/channels/FileChannel
	i32 591810476, ; 6: 0x23464fac => android/os/Bundle
	i32 780408360, ; 7: 0x2e841628 => java/lang/CharSequence
	i32 780987551, ; 8: 0x2e8cec9f => java/io/PrintWriter
	i32 806800039, ; 9: 0x3016caa7 => java/lang/Thread
	i32 857458217, ; 10: 0x331bc629 => android/content/res/AssetManager
	i32 1008962460, ; 11: 0x3c238b9c => android/graphics/Color
	i32 1298454265, ; 12: 0x4d64d6f9 => java/lang/Throwable
	i32 1489594546, ; 13: 0x58c968b2 => java/nio/channels/spi/AbstractInterruptibleChannel
	i32 1506774891, ; 14: 0x59cf8f6b => android/widget/Button
	i32 1573833883, ; 15: 0x5dcecc9b => android/app/AlertDialog
	i32 1586851388, ; 16: 0x5e956e3c => android/os/Handler
	i32 1646348278, ; 17: 0x622147f6 => android/view/View
	i32 1758490869, ; 18: 0x68d070f5 => android/os/BaseBundle
	i32 1807220671, ; 19: 0x6bb7ffbf => android/view/View$OnClickListener
	i32 1851730788, ; 20: 0x6e5f2b64 => java/lang/Runnable
	i32 1859010077, ; 21: 0x6ece3e1d => android/widget/LinearLayout
	i32 1944129628, ; 22: 0x73e1105c => java/io/OutputStream
	i32 1985929388, ; 23: 0x765ee0ac => android/app/Activity
	i32 2027782872, ; 24: 0x78dd82d8 => android/view/ContextThemeWrapper
	i32 2036556174, ; 25: 0x7963618e => android/content/DialogInterface
	i32 2073337312, ; 26: 0x7b949de0 => android/app/AlertDialog$Builder
	i32 2284656609, ; 27: 0x882d17e1 => android/app/Application
	i32 2558143838, ; 28: 0x987a2d5e => java/io/FileInputStream
	i32 2687778660, ; 29: 0xa0343f64 => android/widget/TextView
	i32 2874673969, ; 30: 0xab580b31 => java/lang/StackTraceElement
	i32 2918613155, ; 31: 0xadf680a3 => android/content/DialogInterface$OnClickListener
	i32 2933762856, ; 32: 0xaeddab28 => android/util/AttributeSet
	i32 2942792700, ; 33: 0xaf6773fc => java/lang/Exception
	i32 2983720033, ; 34: 0xb1d7f461 => mono/android/TypeManager
	i32 3032808825, ; 35: 0xb4c4fd79 => java/io/StringWriter
	i32 3576242387, ; 36: 0xd52920d3 => android/runtime/JavaProxyThrowable
	i32 3666243682, ; 37: 0xda867062 => java/lang/String
	i32 3882570516, ; 38: 0xe76b5314 => java/lang/Class
	i32 3900581163, ; 39: 0xe87e252b => java/io/InputStream
	i32 3931120197, ; 40: 0xea502245 => mono/android/content/DialogInterface_OnClickListenerImplementor
	i32 3960999444, ; 41: 0xec180e14 => android/widget/Toast
	i32 3969984744, ; 42: 0xeca128e8 => mono/android/runtime/InputStreamAdapter
	i32 3993327007, ; 43: 0xee05559f => android/content/ContextWrapper
	i32 4020308495, ; 44: 0xefa10a0f => java/lang/Error
	i32 4030673356, ; 45: 0xf03f31cc => android/app/Dialog
	i32 4051772911, ; 46: 0xf18125ef => android/content/Context
	i32 4063591954, ; 47: 0xf2357e12 => crc649583191db32357d4/MainActivity
	i32 4098107575, ; 48: 0xf44428b7 => mono/android/view/View_OnClickListenerImplementor
	i32 4101363546, ; 49: 0xf475d75a => java/io/Writer
	i32 4118878202, ; 50: 0xf58117fa => android/os/Looper
	i32 4157808693 ; 51: 0xf7d32035 => java/io/IOException
], align 4

@module0_managed_to_java = internal dso_local constant [1 x %struct.TypeMapModuleEntry] [
	%struct.TypeMapModuleEntry {
		i32 33554446, ; uint32_t type_token_id (0x200000e)
		i32 47; uint32_t java_map_index (0x2f)
	} ; 0
], align 4

@module1_managed_to_java = internal dso_local constant [51 x %struct.TypeMapModuleEntry] [
	%struct.TypeMapModuleEntry {
		i32 33554488, ; uint32_t type_token_id (0x2000038)
		i32 14; uint32_t java_map_index (0xe)
	}, ; 0
	%struct.TypeMapModuleEntry {
		i32 33554489, ; uint32_t type_token_id (0x2000039)
		i32 21; uint32_t java_map_index (0x15)
	}, ; 1
	%struct.TypeMapModuleEntry {
		i32 33554490, ; uint32_t type_token_id (0x200003a)
		i32 29; uint32_t java_map_index (0x1d)
	}, ; 2
	%struct.TypeMapModuleEntry {
		i32 33554491, ; uint32_t type_token_id (0x200003b)
		i32 41; uint32_t java_map_index (0x29)
	}, ; 3
	%struct.TypeMapModuleEntry {
		i32 33554494, ; uint32_t type_token_id (0x200003e)
		i32 32; uint32_t java_map_index (0x20)
	}, ; 4
	%struct.TypeMapModuleEntry {
		i32 33554497, ; uint32_t type_token_id (0x2000041)
		i32 18; uint32_t java_map_index (0x12)
	}, ; 5
	%struct.TypeMapModuleEntry {
		i32 33554498, ; uint32_t type_token_id (0x2000042)
		i32 6; uint32_t java_map_index (0x6)
	}, ; 6
	%struct.TypeMapModuleEntry {
		i32 33554499, ; uint32_t type_token_id (0x2000043)
		i32 16; uint32_t java_map_index (0x10)
	}, ; 7
	%struct.TypeMapModuleEntry {
		i32 33554500, ; uint32_t type_token_id (0x2000044)
		i32 50; uint32_t java_map_index (0x32)
	}, ; 8
	%struct.TypeMapModuleEntry {
		i32 33554502, ; uint32_t type_token_id (0x2000046)
		i32 23; uint32_t java_map_index (0x17)
	}, ; 9
	%struct.TypeMapModuleEntry {
		i32 33554503, ; uint32_t type_token_id (0x2000047)
		i32 15; uint32_t java_map_index (0xf)
	}, ; 10
	%struct.TypeMapModuleEntry {
		i32 33554504, ; uint32_t type_token_id (0x2000048)
		i32 26; uint32_t java_map_index (0x1a)
	}, ; 11
	%struct.TypeMapModuleEntry {
		i32 33554505, ; uint32_t type_token_id (0x2000049)
		i32 27; uint32_t java_map_index (0x1b)
	}, ; 12
	%struct.TypeMapModuleEntry {
		i32 33554506, ; uint32_t type_token_id (0x200004a)
		i32 45; uint32_t java_map_index (0x2d)
	}, ; 13
	%struct.TypeMapModuleEntry {
		i32 33554510, ; uint32_t type_token_id (0x200004e)
		i32 24; uint32_t java_map_index (0x18)
	}, ; 14
	%struct.TypeMapModuleEntry {
		i32 33554511, ; uint32_t type_token_id (0x200004f)
		i32 17; uint32_t java_map_index (0x11)
	}, ; 15
	%struct.TypeMapModuleEntry {
		i32 33554512, ; uint32_t type_token_id (0x2000050)
		i32 19; uint32_t java_map_index (0x13)
	}, ; 16
	%struct.TypeMapModuleEntry {
		i32 33554514, ; uint32_t type_token_id (0x2000052)
		i32 48; uint32_t java_map_index (0x30)
	}, ; 17
	%struct.TypeMapModuleEntry {
		i32 33554518, ; uint32_t type_token_id (0x2000056)
		i32 1; uint32_t java_map_index (0x1)
	}, ; 18
	%struct.TypeMapModuleEntry {
		i32 33554519, ; uint32_t type_token_id (0x2000057)
		i32 2; uint32_t java_map_index (0x2)
	}, ; 19
	%struct.TypeMapModuleEntry {
		i32 33554541, ; uint32_t type_token_id (0x200006d)
		i32 42; uint32_t java_map_index (0x2a)
	}, ; 20
	%struct.TypeMapModuleEntry {
		i32 33554543, ; uint32_t type_token_id (0x200006f)
		i32 36; uint32_t java_map_index (0x24)
	}, ; 21
	%struct.TypeMapModuleEntry {
		i32 33554554, ; uint32_t type_token_id (0x200007a)
		i32 4; uint32_t java_map_index (0x4)
	}, ; 22
	%struct.TypeMapModuleEntry {
		i32 33554560, ; uint32_t type_token_id (0x2000080)
		i32 11; uint32_t java_map_index (0xb)
	}, ; 23
	%struct.TypeMapModuleEntry {
		i32 33554563, ; uint32_t type_token_id (0x2000083)
		i32 46; uint32_t java_map_index (0x2e)
	}, ; 24
	%struct.TypeMapModuleEntry {
		i32 33554565, ; uint32_t type_token_id (0x2000085)
		i32 43; uint32_t java_map_index (0x2b)
	}, ; 25
	%struct.TypeMapModuleEntry {
		i32 33554566, ; uint32_t type_token_id (0x2000086)
		i32 31; uint32_t java_map_index (0x1f)
	}, ; 26
	%struct.TypeMapModuleEntry {
		i32 33554569, ; uint32_t type_token_id (0x2000089)
		i32 40; uint32_t java_map_index (0x28)
	}, ; 27
	%struct.TypeMapModuleEntry {
		i32 33554570, ; uint32_t type_token_id (0x200008a)
		i32 25; uint32_t java_map_index (0x19)
	}, ; 28
	%struct.TypeMapModuleEntry {
		i32 33554572, ; uint32_t type_token_id (0x200008c)
		i32 10; uint32_t java_map_index (0xa)
	}, ; 29
	%struct.TypeMapModuleEntry {
		i32 33554573, ; uint32_t type_token_id (0x200008d)
		i32 5; uint32_t java_map_index (0x5)
	}, ; 30
	%struct.TypeMapModuleEntry {
		i32 33554575, ; uint32_t type_token_id (0x200008f)
		i32 13; uint32_t java_map_index (0xd)
	}, ; 31
	%struct.TypeMapModuleEntry {
		i32 33554577, ; uint32_t type_token_id (0x2000091)
		i32 28; uint32_t java_map_index (0x1c)
	}, ; 32
	%struct.TypeMapModuleEntry {
		i32 33554578, ; uint32_t type_token_id (0x2000092)
		i32 39; uint32_t java_map_index (0x27)
	}, ; 33
	%struct.TypeMapModuleEntry {
		i32 33554580, ; uint32_t type_token_id (0x2000094)
		i32 51; uint32_t java_map_index (0x33)
	}, ; 34
	%struct.TypeMapModuleEntry {
		i32 33554581, ; uint32_t type_token_id (0x2000095)
		i32 22; uint32_t java_map_index (0x16)
	}, ; 35
	%struct.TypeMapModuleEntry {
		i32 33554583, ; uint32_t type_token_id (0x2000097)
		i32 8; uint32_t java_map_index (0x8)
	}, ; 36
	%struct.TypeMapModuleEntry {
		i32 33554584, ; uint32_t type_token_id (0x2000098)
		i32 35; uint32_t java_map_index (0x23)
	}, ; 37
	%struct.TypeMapModuleEntry {
		i32 33554585, ; uint32_t type_token_id (0x2000099)
		i32 49; uint32_t java_map_index (0x31)
	}, ; 38
	%struct.TypeMapModuleEntry {
		i32 33554587, ; uint32_t type_token_id (0x200009b)
		i32 38; uint32_t java_map_index (0x26)
	}, ; 39
	%struct.TypeMapModuleEntry {
		i32 33554588, ; uint32_t type_token_id (0x200009c)
		i32 44; uint32_t java_map_index (0x2c)
	}, ; 40
	%struct.TypeMapModuleEntry {
		i32 33554589, ; uint32_t type_token_id (0x200009d)
		i32 33; uint32_t java_map_index (0x21)
	}, ; 41
	%struct.TypeMapModuleEntry {
		i32 33554590, ; uint32_t type_token_id (0x200009e)
		i32 7; uint32_t java_map_index (0x7)
	}, ; 42
	%struct.TypeMapModuleEntry {
		i32 33554593, ; uint32_t type_token_id (0x20000a1)
		i32 20; uint32_t java_map_index (0x14)
	}, ; 43
	%struct.TypeMapModuleEntry {
		i32 33554595, ; uint32_t type_token_id (0x20000a3)
		i32 0; uint32_t java_map_index (0x0)
	}, ; 44
	%struct.TypeMapModuleEntry {
		i32 33554596, ; uint32_t type_token_id (0x20000a4)
		i32 30; uint32_t java_map_index (0x1e)
	}, ; 45
	%struct.TypeMapModuleEntry {
		i32 33554597, ; uint32_t type_token_id (0x20000a5)
		i32 37; uint32_t java_map_index (0x25)
	}, ; 46
	%struct.TypeMapModuleEntry {
		i32 33554599, ; uint32_t type_token_id (0x20000a7)
		i32 9; uint32_t java_map_index (0x9)
	}, ; 47
	%struct.TypeMapModuleEntry {
		i32 33554600, ; uint32_t type_token_id (0x20000a8)
		i32 3; uint32_t java_map_index (0x3)
	}, ; 48
	%struct.TypeMapModuleEntry {
		i32 33554601, ; uint32_t type_token_id (0x20000a9)
		i32 12; uint32_t java_map_index (0xc)
	}, ; 49
	%struct.TypeMapModuleEntry {
		i32 33554613, ; uint32_t type_token_id (0x20000b5)
		i32 34; uint32_t java_map_index (0x22)
	} ; 50
], align 4

@module1_managed_to_java_duplicates = internal dso_local constant [13 x %struct.TypeMapModuleEntry] [
	%struct.TypeMapModuleEntry {
		i32 33554495, ; uint32_t type_token_id (0x200003f)
		i32 32; uint32_t java_map_index (0x20)
	}, ; 0
	%struct.TypeMapModuleEntry {
		i32 33554513, ; uint32_t type_token_id (0x2000051)
		i32 19; uint32_t java_map_index (0x13)
	}, ; 1
	%struct.TypeMapModuleEntry {
		i32 33554520, ; uint32_t type_token_id (0x2000058)
		i32 1; uint32_t java_map_index (0x1)
	}, ; 2
	%struct.TypeMapModuleEntry {
		i32 33554564, ; uint32_t type_token_id (0x2000084)
		i32 46; uint32_t java_map_index (0x2e)
	}, ; 3
	%struct.TypeMapModuleEntry {
		i32 33554567, ; uint32_t type_token_id (0x2000087)
		i32 31; uint32_t java_map_index (0x1f)
	}, ; 4
	%struct.TypeMapModuleEntry {
		i32 33554571, ; uint32_t type_token_id (0x200008b)
		i32 25; uint32_t java_map_index (0x19)
	}, ; 5
	%struct.TypeMapModuleEntry {
		i32 33554574, ; uint32_t type_token_id (0x200008e)
		i32 5; uint32_t java_map_index (0x5)
	}, ; 6
	%struct.TypeMapModuleEntry {
		i32 33554576, ; uint32_t type_token_id (0x2000090)
		i32 13; uint32_t java_map_index (0xd)
	}, ; 7
	%struct.TypeMapModuleEntry {
		i32 33554579, ; uint32_t type_token_id (0x2000093)
		i32 39; uint32_t java_map_index (0x27)
	}, ; 8
	%struct.TypeMapModuleEntry {
		i32 33554582, ; uint32_t type_token_id (0x2000096)
		i32 22; uint32_t java_map_index (0x16)
	}, ; 9
	%struct.TypeMapModuleEntry {
		i32 33554586, ; uint32_t type_token_id (0x200009a)
		i32 49; uint32_t java_map_index (0x31)
	}, ; 10
	%struct.TypeMapModuleEntry {
		i32 33554591, ; uint32_t type_token_id (0x200009f)
		i32 7; uint32_t java_map_index (0x7)
	}, ; 11
	%struct.TypeMapModuleEntry {
		i32 33554594, ; uint32_t type_token_id (0x20000a2)
		i32 20; uint32_t java_map_index (0x14)
	} ; 12
], align 4

; Java to managed map
@map_java = dso_local local_unnamed_addr constant [52 x %struct.TypeMapJava] [
	%struct.TypeMapJava {
		i32 1, ; uint32_t module_index (0x1)
		i32 33554595, ; uint32_t type_token_id (0x20000a3)
		i32 45; uint32_t java_name_index (0x2d)
	}, ; 0
	%struct.TypeMapJava {
		i32 1, ; uint32_t module_index (0x1)
		i32 33554518, ; uint32_t type_token_id (0x2000056)
		i32 19; uint32_t java_name_index (0x13)
	}, ; 1
	%struct.TypeMapJava {
		i32 1, ; uint32_t module_index (0x1)
		i32 33554519, ; uint32_t type_token_id (0x2000057)
		i32 20; uint32_t java_name_index (0x14)
	}, ; 2
	%struct.TypeMapJava {
		i32 1, ; uint32_t module_index (0x1)
		i32 33554600, ; uint32_t type_token_id (0x20000a8)
		i32 49; uint32_t java_name_index (0x31)
	}, ; 3
	%struct.TypeMapJava {
		i32 1, ; uint32_t module_index (0x1)
		i32 33554554, ; uint32_t type_token_id (0x200007a)
		i32 23; uint32_t java_name_index (0x17)
	}, ; 4
	%struct.TypeMapJava {
		i32 1, ; uint32_t module_index (0x1)
		i32 33554573, ; uint32_t type_token_id (0x200008d)
		i32 31; uint32_t java_name_index (0x1f)
	}, ; 5
	%struct.TypeMapJava {
		i32 1, ; uint32_t module_index (0x1)
		i32 33554498, ; uint32_t type_token_id (0x2000042)
		i32 7; uint32_t java_name_index (0x7)
	}, ; 6
	%struct.TypeMapJava {
		i32 1, ; uint32_t module_index (0x1)
		i32 0, ; uint32_t type_token_id (0x0)
		i32 43; uint32_t java_name_index (0x2b)
	}, ; 7
	%struct.TypeMapJava {
		i32 1, ; uint32_t module_index (0x1)
		i32 33554583, ; uint32_t type_token_id (0x2000097)
		i32 37; uint32_t java_name_index (0x25)
	}, ; 8
	%struct.TypeMapJava {
		i32 1, ; uint32_t module_index (0x1)
		i32 33554599, ; uint32_t type_token_id (0x20000a7)
		i32 48; uint32_t java_name_index (0x30)
	}, ; 9
	%struct.TypeMapJava {
		i32 1, ; uint32_t module_index (0x1)
		i32 33554572, ; uint32_t type_token_id (0x200008c)
		i32 30; uint32_t java_name_index (0x1e)
	}, ; 10
	%struct.TypeMapJava {
		i32 1, ; uint32_t module_index (0x1)
		i32 33554560, ; uint32_t type_token_id (0x2000080)
		i32 24; uint32_t java_name_index (0x18)
	}, ; 11
	%struct.TypeMapJava {
		i32 1, ; uint32_t module_index (0x1)
		i32 33554601, ; uint32_t type_token_id (0x20000a9)
		i32 50; uint32_t java_name_index (0x32)
	}, ; 12
	%struct.TypeMapJava {
		i32 1, ; uint32_t module_index (0x1)
		i32 33554575, ; uint32_t type_token_id (0x200008f)
		i32 32; uint32_t java_name_index (0x20)
	}, ; 13
	%struct.TypeMapJava {
		i32 1, ; uint32_t module_index (0x1)
		i32 33554488, ; uint32_t type_token_id (0x2000038)
		i32 1; uint32_t java_name_index (0x1)
	}, ; 14
	%struct.TypeMapJava {
		i32 1, ; uint32_t module_index (0x1)
		i32 33554503, ; uint32_t type_token_id (0x2000047)
		i32 11; uint32_t java_name_index (0xb)
	}, ; 15
	%struct.TypeMapJava {
		i32 1, ; uint32_t module_index (0x1)
		i32 33554499, ; uint32_t type_token_id (0x2000043)
		i32 8; uint32_t java_name_index (0x8)
	}, ; 16
	%struct.TypeMapJava {
		i32 1, ; uint32_t module_index (0x1)
		i32 33554511, ; uint32_t type_token_id (0x200004f)
		i32 16; uint32_t java_name_index (0x10)
	}, ; 17
	%struct.TypeMapJava {
		i32 1, ; uint32_t module_index (0x1)
		i32 33554497, ; uint32_t type_token_id (0x2000041)
		i32 6; uint32_t java_name_index (0x6)
	}, ; 18
	%struct.TypeMapJava {
		i32 1, ; uint32_t module_index (0x1)
		i32 0, ; uint32_t type_token_id (0x0)
		i32 17; uint32_t java_name_index (0x11)
	}, ; 19
	%struct.TypeMapJava {
		i32 1, ; uint32_t module_index (0x1)
		i32 0, ; uint32_t type_token_id (0x0)
		i32 44; uint32_t java_name_index (0x2c)
	}, ; 20
	%struct.TypeMapJava {
		i32 1, ; uint32_t module_index (0x1)
		i32 33554489, ; uint32_t type_token_id (0x2000039)
		i32 2; uint32_t java_name_index (0x2)
	}, ; 21
	%struct.TypeMapJava {
		i32 1, ; uint32_t module_index (0x1)
		i32 33554581, ; uint32_t type_token_id (0x2000095)
		i32 36; uint32_t java_name_index (0x24)
	}, ; 22
	%struct.TypeMapJava {
		i32 1, ; uint32_t module_index (0x1)
		i32 33554502, ; uint32_t type_token_id (0x2000046)
		i32 10; uint32_t java_name_index (0xa)
	}, ; 23
	%struct.TypeMapJava {
		i32 1, ; uint32_t module_index (0x1)
		i32 33554510, ; uint32_t type_token_id (0x200004e)
		i32 15; uint32_t java_name_index (0xf)
	}, ; 24
	%struct.TypeMapJava {
		i32 1, ; uint32_t module_index (0x1)
		i32 0, ; uint32_t type_token_id (0x0)
		i32 29; uint32_t java_name_index (0x1d)
	}, ; 25
	%struct.TypeMapJava {
		i32 1, ; uint32_t module_index (0x1)
		i32 33554504, ; uint32_t type_token_id (0x2000048)
		i32 12; uint32_t java_name_index (0xc)
	}, ; 26
	%struct.TypeMapJava {
		i32 1, ; uint32_t module_index (0x1)
		i32 33554505, ; uint32_t type_token_id (0x2000049)
		i32 13; uint32_t java_name_index (0xd)
	}, ; 27
	%struct.TypeMapJava {
		i32 1, ; uint32_t module_index (0x1)
		i32 33554577, ; uint32_t type_token_id (0x2000091)
		i32 33; uint32_t java_name_index (0x21)
	}, ; 28
	%struct.TypeMapJava {
		i32 1, ; uint32_t module_index (0x1)
		i32 33554490, ; uint32_t type_token_id (0x200003a)
		i32 3; uint32_t java_name_index (0x3)
	}, ; 29
	%struct.TypeMapJava {
		i32 1, ; uint32_t module_index (0x1)
		i32 33554596, ; uint32_t type_token_id (0x20000a4)
		i32 46; uint32_t java_name_index (0x2e)
	}, ; 30
	%struct.TypeMapJava {
		i32 1, ; uint32_t module_index (0x1)
		i32 0, ; uint32_t type_token_id (0x0)
		i32 27; uint32_t java_name_index (0x1b)
	}, ; 31
	%struct.TypeMapJava {
		i32 1, ; uint32_t module_index (0x1)
		i32 0, ; uint32_t type_token_id (0x0)
		i32 5; uint32_t java_name_index (0x5)
	}, ; 32
	%struct.TypeMapJava {
		i32 1, ; uint32_t module_index (0x1)
		i32 33554589, ; uint32_t type_token_id (0x200009d)
		i32 42; uint32_t java_name_index (0x2a)
	}, ; 33
	%struct.TypeMapJava {
		i32 1, ; uint32_t module_index (0x1)
		i32 33554613, ; uint32_t type_token_id (0x20000b5)
		i32 51; uint32_t java_name_index (0x33)
	}, ; 34
	%struct.TypeMapJava {
		i32 1, ; uint32_t module_index (0x1)
		i32 33554584, ; uint32_t type_token_id (0x2000098)
		i32 38; uint32_t java_name_index (0x26)
	}, ; 35
	%struct.TypeMapJava {
		i32 1, ; uint32_t module_index (0x1)
		i32 33554543, ; uint32_t type_token_id (0x200006f)
		i32 22; uint32_t java_name_index (0x16)
	}, ; 36
	%struct.TypeMapJava {
		i32 1, ; uint32_t module_index (0x1)
		i32 33554597, ; uint32_t type_token_id (0x20000a5)
		i32 47; uint32_t java_name_index (0x2f)
	}, ; 37
	%struct.TypeMapJava {
		i32 1, ; uint32_t module_index (0x1)
		i32 33554587, ; uint32_t type_token_id (0x200009b)
		i32 40; uint32_t java_name_index (0x28)
	}, ; 38
	%struct.TypeMapJava {
		i32 1, ; uint32_t module_index (0x1)
		i32 33554578, ; uint32_t type_token_id (0x2000092)
		i32 34; uint32_t java_name_index (0x22)
	}, ; 39
	%struct.TypeMapJava {
		i32 1, ; uint32_t module_index (0x1)
		i32 33554569, ; uint32_t type_token_id (0x2000089)
		i32 28; uint32_t java_name_index (0x1c)
	}, ; 40
	%struct.TypeMapJava {
		i32 1, ; uint32_t module_index (0x1)
		i32 33554491, ; uint32_t type_token_id (0x200003b)
		i32 4; uint32_t java_name_index (0x4)
	}, ; 41
	%struct.TypeMapJava {
		i32 1, ; uint32_t module_index (0x1)
		i32 33554541, ; uint32_t type_token_id (0x200006d)
		i32 21; uint32_t java_name_index (0x15)
	}, ; 42
	%struct.TypeMapJava {
		i32 1, ; uint32_t module_index (0x1)
		i32 33554565, ; uint32_t type_token_id (0x2000085)
		i32 26; uint32_t java_name_index (0x1a)
	}, ; 43
	%struct.TypeMapJava {
		i32 1, ; uint32_t module_index (0x1)
		i32 33554588, ; uint32_t type_token_id (0x200009c)
		i32 41; uint32_t java_name_index (0x29)
	}, ; 44
	%struct.TypeMapJava {
		i32 1, ; uint32_t module_index (0x1)
		i32 33554506, ; uint32_t type_token_id (0x200004a)
		i32 14; uint32_t java_name_index (0xe)
	}, ; 45
	%struct.TypeMapJava {
		i32 1, ; uint32_t module_index (0x1)
		i32 33554563, ; uint32_t type_token_id (0x2000083)
		i32 25; uint32_t java_name_index (0x19)
	}, ; 46
	%struct.TypeMapJava {
		i32 0, ; uint32_t module_index (0x0)
		i32 33554446, ; uint32_t type_token_id (0x200000e)
		i32 0; uint32_t java_name_index (0x0)
	}, ; 47
	%struct.TypeMapJava {
		i32 1, ; uint32_t module_index (0x1)
		i32 33554514, ; uint32_t type_token_id (0x2000052)
		i32 18; uint32_t java_name_index (0x12)
	}, ; 48
	%struct.TypeMapJava {
		i32 1, ; uint32_t module_index (0x1)
		i32 33554585, ; uint32_t type_token_id (0x2000099)
		i32 39; uint32_t java_name_index (0x27)
	}, ; 49
	%struct.TypeMapJava {
		i32 1, ; uint32_t module_index (0x1)
		i32 33554500, ; uint32_t type_token_id (0x2000044)
		i32 9; uint32_t java_name_index (0x9)
	}, ; 50
	%struct.TypeMapJava {
		i32 1, ; uint32_t module_index (0x1)
		i32 33554580, ; uint32_t type_token_id (0x2000094)
		i32 35; uint32_t java_name_index (0x23)
	} ; 51
], align 4

; Java type names
@java_type_names = dso_local local_unnamed_addr constant [52 x ptr] [
	ptr @.str.0, ; 0
	ptr @.str.1, ; 1
	ptr @.str.2, ; 2
	ptr @.str.3, ; 3
	ptr @.str.4, ; 4
	ptr @.str.5, ; 5
	ptr @.str.6, ; 6
	ptr @.str.7, ; 7
	ptr @.str.8, ; 8
	ptr @.str.9, ; 9
	ptr @.str.10, ; 10
	ptr @.str.11, ; 11
	ptr @.str.12, ; 12
	ptr @.str.13, ; 13
	ptr @.str.14, ; 14
	ptr @.str.15, ; 15
	ptr @.str.16, ; 16
	ptr @.str.17, ; 17
	ptr @.str.18, ; 18
	ptr @.str.19, ; 19
	ptr @.str.20, ; 20
	ptr @.str.21, ; 21
	ptr @.str.22, ; 22
	ptr @.str.23, ; 23
	ptr @.str.24, ; 24
	ptr @.str.25, ; 25
	ptr @.str.26, ; 26
	ptr @.str.27, ; 27
	ptr @.str.28, ; 28
	ptr @.str.29, ; 29
	ptr @.str.30, ; 30
	ptr @.str.31, ; 31
	ptr @.str.32, ; 32
	ptr @.str.33, ; 33
	ptr @.str.34, ; 34
	ptr @.str.35, ; 35
	ptr @.str.36, ; 36
	ptr @.str.37, ; 37
	ptr @.str.38, ; 38
	ptr @.str.39, ; 39
	ptr @.str.40, ; 40
	ptr @.str.41, ; 41
	ptr @.str.42, ; 42
	ptr @.str.43, ; 43
	ptr @.str.44, ; 44
	ptr @.str.45, ; 45
	ptr @.str.46, ; 46
	ptr @.str.47, ; 47
	ptr @.str.48, ; 48
	ptr @.str.49, ; 49
	ptr @.str.50, ; 50
	ptr @.str.51 ; 51
], align 4

; Strings
@.str.0 = private unnamed_addr constant [35 x i8] c"crc649583191db32357d4/MainActivity\00", align 1
@.str.1 = private unnamed_addr constant [22 x i8] c"android/widget/Button\00", align 1
@.str.2 = private unnamed_addr constant [28 x i8] c"android/widget/LinearLayout\00", align 1
@.str.3 = private unnamed_addr constant [24 x i8] c"android/widget/TextView\00", align 1
@.str.4 = private unnamed_addr constant [21 x i8] c"android/widget/Toast\00", align 1
@.str.5 = private unnamed_addr constant [26 x i8] c"android/util/AttributeSet\00", align 1
@.str.6 = private unnamed_addr constant [22 x i8] c"android/os/BaseBundle\00", align 1
@.str.7 = private unnamed_addr constant [18 x i8] c"android/os/Bundle\00", align 1
@.str.8 = private unnamed_addr constant [19 x i8] c"android/os/Handler\00", align 1
@.str.9 = private unnamed_addr constant [18 x i8] c"android/os/Looper\00", align 1
@.str.10 = private unnamed_addr constant [21 x i8] c"android/app/Activity\00", align 1
@.str.11 = private unnamed_addr constant [24 x i8] c"android/app/AlertDialog\00", align 1
@.str.12 = private unnamed_addr constant [32 x i8] c"android/app/AlertDialog$Builder\00", align 1
@.str.13 = private unnamed_addr constant [24 x i8] c"android/app/Application\00", align 1
@.str.14 = private unnamed_addr constant [19 x i8] c"android/app/Dialog\00", align 1
@.str.15 = private unnamed_addr constant [33 x i8] c"android/view/ContextThemeWrapper\00", align 1
@.str.16 = private unnamed_addr constant [18 x i8] c"android/view/View\00", align 1
@.str.17 = private unnamed_addr constant [34 x i8] c"android/view/View$OnClickListener\00", align 1
@.str.18 = private unnamed_addr constant [50 x i8] c"mono/android/view/View_OnClickListenerImplementor\00", align 1
@.str.19 = private unnamed_addr constant [23 x i8] c"android/view/ViewGroup\00", align 1
@.str.20 = private unnamed_addr constant [36 x i8] c"android/view/ViewGroup$LayoutParams\00", align 1
@.str.21 = private unnamed_addr constant [40 x i8] c"mono/android/runtime/InputStreamAdapter\00", align 1
@.str.22 = private unnamed_addr constant [35 x i8] c"android/runtime/JavaProxyThrowable\00", align 1
@.str.23 = private unnamed_addr constant [41 x i8] c"mono/android/runtime/OutputStreamAdapter\00", align 1
@.str.24 = private unnamed_addr constant [23 x i8] c"android/graphics/Color\00", align 1
@.str.25 = private unnamed_addr constant [24 x i8] c"android/content/Context\00", align 1
@.str.26 = private unnamed_addr constant [31 x i8] c"android/content/ContextWrapper\00", align 1
@.str.27 = private unnamed_addr constant [48 x i8] c"android/content/DialogInterface$OnClickListener\00", align 1
@.str.28 = private unnamed_addr constant [64 x i8] c"mono/android/content/DialogInterface_OnClickListenerImplementor\00", align 1
@.str.29 = private unnamed_addr constant [32 x i8] c"android/content/DialogInterface\00", align 1
@.str.30 = private unnamed_addr constant [33 x i8] c"android/content/res/AssetManager\00", align 1
@.str.31 = private unnamed_addr constant [30 x i8] c"java/nio/channels/FileChannel\00", align 1
@.str.32 = private unnamed_addr constant [51 x i8] c"java/nio/channels/spi/AbstractInterruptibleChannel\00", align 1
@.str.33 = private unnamed_addr constant [24 x i8] c"java/io/FileInputStream\00", align 1
@.str.34 = private unnamed_addr constant [20 x i8] c"java/io/InputStream\00", align 1
@.str.35 = private unnamed_addr constant [20 x i8] c"java/io/IOException\00", align 1
@.str.36 = private unnamed_addr constant [21 x i8] c"java/io/OutputStream\00", align 1
@.str.37 = private unnamed_addr constant [20 x i8] c"java/io/PrintWriter\00", align 1
@.str.38 = private unnamed_addr constant [21 x i8] c"java/io/StringWriter\00", align 1
@.str.39 = private unnamed_addr constant [15 x i8] c"java/io/Writer\00", align 1
@.str.40 = private unnamed_addr constant [16 x i8] c"java/lang/Class\00", align 1
@.str.41 = private unnamed_addr constant [16 x i8] c"java/lang/Error\00", align 1
@.str.42 = private unnamed_addr constant [20 x i8] c"java/lang/Exception\00", align 1
@.str.43 = private unnamed_addr constant [23 x i8] c"java/lang/CharSequence\00", align 1
@.str.44 = private unnamed_addr constant [19 x i8] c"java/lang/Runnable\00", align 1
@.str.45 = private unnamed_addr constant [17 x i8] c"java/lang/Object\00", align 1
@.str.46 = private unnamed_addr constant [28 x i8] c"java/lang/StackTraceElement\00", align 1
@.str.47 = private unnamed_addr constant [17 x i8] c"java/lang/String\00", align 1
@.str.48 = private unnamed_addr constant [17 x i8] c"java/lang/Thread\00", align 1
@.str.49 = private unnamed_addr constant [35 x i8] c"mono/java/lang/RunnableImplementor\00", align 1
@.str.50 = private unnamed_addr constant [20 x i8] c"java/lang/Throwable\00", align 1
@.str.51 = private unnamed_addr constant [25 x i8] c"mono/android/TypeManager\00", align 1

;TypeMapModule
@.TypeMapModule.0_assembly_name = private unnamed_addr constant [10 x i8] c"MauiMilan\00", align 1
@.TypeMapModule.1_assembly_name = private unnamed_addr constant [13 x i8] c"Mono.Android\00", align 1

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
!7 = !{i32 1, !"min_enum_size", i32 4}
