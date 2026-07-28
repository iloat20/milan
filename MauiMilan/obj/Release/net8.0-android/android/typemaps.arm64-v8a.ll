; ModuleID = 'typemaps.arm64-v8a.ll'
source_filename = "typemaps.arm64-v8a.ll"
target datalayout = "e-m:e-i8:8:32-i16:16:32-i64:64-i128:128-n32:64-S128"
target triple = "aarch64-unknown-linux-android21"

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
], align 8

; Java types name hashes
@map_java_hashes = dso_local local_unnamed_addr constant [52 x i64] [
	i64 128182020419974451, ; 0: 0x1c764de51b97533 => java/lang/String
	i64 318564728890166633, ; 1: 0x46bc4eedf778d69 => android/widget/Button
	i64 361870449891484378, ; 2: 0x5059f41c47e22da => android/os/Bundle
	i64 363417747702605178, ; 3: 0x50b1e841ce2e57a => android/widget/TextView
	i64 698738878519169148, ; 4: 0x9b26b4ed4e3d07c => mono/android/content/DialogInterface_OnClickListenerImplementor
	i64 870874870088288028, ; 5: 0xc15f8148b6d471c => java/lang/Exception
	i64 1317579852464953526, ; 6: 0x1248fbe51d6298b6 => java/io/FileInputStream
	i64 1320822650197077237, ; 7: 0x12548133cc496cf5 => android/runtime/JavaProxyThrowable
	i64 1747499027921055994, ; 8: 0x18405d1b749330fa => android/os/BaseBundle
	i64 1831728799718484971, ; 9: 0x196b9ba37012abeb => java/io/IOException
	i64 2164140653916027403, ; 10: 0x1e08927568a57a0b => java/io/InputStream
	i64 3312753486604898190, ; 11: 0x2df943be8d858f8e => android/app/Dialog
	i64 3403987850230942668, ; 12: 0x2f3d64ea28c2cbcc => crc649583191db32357d4/MainActivity
	i64 3476617847597562063, ; 13: 0x303f6d8331d5f8cf => java/io/PrintWriter
	i64 3530631042196079534, ; 14: 0x30ff523a0f1083ae => android/content/DialogInterface
	i64 4305371449952891808, ; 15: 0x3bbfc085dc8cf3a0 => java/lang/Class
	i64 5214467817578676657, ; 16: 0x485d82da477bc1b1 => java/lang/Error
	i64 6116679261601087867, ; 17: 0x54e2cf6180bb417b => android/widget/LinearLayout
	i64 8190305621607579207, ; 18: 0x71a9cf9199cdfe47 => java/nio/channels/spi/AbstractInterruptibleChannel
	i64 8487642170263250902, ; 19: 0x75ca29959b2aa7d6 => android/content/ContextWrapper
	i64 8587172038193766563, ; 20: 0x772bc378d1b4e0a3 => java/lang/Runnable
	i64 9478593896738967145, ; 21: 0x838abaede94fce69 => android/widget/Toast
	i64 9667515047141612341, ; 22: 0x8629e9b6f59e9b35 => java/lang/Thread
	i64 9869939015140501507, ; 23: 0x88f9113db837e803 => android/app/Activity
	i64 10499957734077086001, ; 24: 0x91b757ed9047b931 => android/view/ViewGroup$LayoutParams
	i64 11112718717483603117, ; 25: 0x9a384ecbbc71d4ad => android/os/Handler
	i64 11573301743732151818, ; 26: 0xa09ca09e3190560a => mono/java/lang/RunnableImplementor
	i64 11954228872253987625, ; 27: 0xa5e5f3d2b66adb29 => android/view/View
	i64 12228984007958404582, ; 28: 0xa9b61429ce4b1de6 => android/content/Context
	i64 12476375190645835422, ; 29: 0xad24fd221af1069e => android/os/Looper
	i64 13191394589072141775, ; 30: 0xb7113f7cdda7adcf => android/app/AlertDialog$Builder
	i64 13402779434266666368, ; 31: 0xba003ce26e602580 => mono/android/TypeManager
	i64 13491848569179882038, ; 32: 0xbb3cacca71544236 => android/app/AlertDialog
	i64 13770727111868296170, ; 33: 0xbf1b735909c02bea => java/io/StringWriter
	i64 13805562342397192842, ; 34: 0xbf9735ce2f182a8a => android/util/AttributeSet
	i64 13877554026709814142, ; 35: 0xc096f9dc61548b7e => android/view/View$OnClickListener
	i64 14031640676547298208, ; 36: 0xc2ba66da3d8603a0 => java/nio/channels/FileChannel
	i64 14167891754637755728, ; 37: 0xc49e767c735e8550 => java/lang/Object
	i64 14206023932851353817, ; 38: 0xc525ef800c4d78d9 => mono/android/runtime/OutputStreamAdapter
	i64 14649586819325063784, ; 39: 0xcb4dc998681d7268 => mono/android/view/View_OnClickListenerImplementor
	i64 14684559126920293129, ; 40: 0xcbca08b94b4deb09 => java/lang/CharSequence
	i64 14830759644181035942, ; 41: 0xcdd17151d49bfba6 => android/content/res/AssetManager
	i64 14940408132235664607, ; 42: 0xcf56fe09e1439cdf => java/lang/Throwable
	i64 15142650489578038267, ; 43: 0xd22580641d31a3fb => java/lang/StackTraceElement
	i64 15633873768898914415, ; 44: 0xd8f6ad5c6a84686f => java/io/Writer
	i64 16314168557433322311, ; 45: 0xe26791dde7a8fb47 => android/view/ContextThemeWrapper
	i64 16413651262945443612, ; 46: 0xe3c900dc43013f1c => android/content/DialogInterface$OnClickListener
	i64 16542847110558016359, ; 47: 0xe593ffcc9e686367 => android/app/Application
	i64 16723123314454325679, ; 48: 0xe814780d351a69af => mono/android/runtime/InputStreamAdapter
	i64 16832017439803262409, ; 49: 0xe99756ae80a745c9 => android/view/ViewGroup
	i64 17125416866214736517, ; 50: 0xeda9b3e7cd367285 => java/io/OutputStream
	i64 17356379843024169959 ; 51: 0xf0de3f825a34abe7 => android/graphics/Color
], align 8

@module0_managed_to_java = internal dso_local constant [1 x %struct.TypeMapModuleEntry] [
	%struct.TypeMapModuleEntry {
		i32 33554446, ; uint32_t type_token_id (0x200000e)
		i32 12; uint32_t java_map_index (0xc)
	} ; 0
], align 4

@module1_managed_to_java = internal dso_local constant [51 x %struct.TypeMapModuleEntry] [
	%struct.TypeMapModuleEntry {
		i32 33554488, ; uint32_t type_token_id (0x2000038)
		i32 1; uint32_t java_map_index (0x1)
	}, ; 0
	%struct.TypeMapModuleEntry {
		i32 33554489, ; uint32_t type_token_id (0x2000039)
		i32 17; uint32_t java_map_index (0x11)
	}, ; 1
	%struct.TypeMapModuleEntry {
		i32 33554490, ; uint32_t type_token_id (0x200003a)
		i32 3; uint32_t java_map_index (0x3)
	}, ; 2
	%struct.TypeMapModuleEntry {
		i32 33554491, ; uint32_t type_token_id (0x200003b)
		i32 21; uint32_t java_map_index (0x15)
	}, ; 3
	%struct.TypeMapModuleEntry {
		i32 33554494, ; uint32_t type_token_id (0x200003e)
		i32 34; uint32_t java_map_index (0x22)
	}, ; 4
	%struct.TypeMapModuleEntry {
		i32 33554497, ; uint32_t type_token_id (0x2000041)
		i32 8; uint32_t java_map_index (0x8)
	}, ; 5
	%struct.TypeMapModuleEntry {
		i32 33554498, ; uint32_t type_token_id (0x2000042)
		i32 2; uint32_t java_map_index (0x2)
	}, ; 6
	%struct.TypeMapModuleEntry {
		i32 33554499, ; uint32_t type_token_id (0x2000043)
		i32 25; uint32_t java_map_index (0x19)
	}, ; 7
	%struct.TypeMapModuleEntry {
		i32 33554500, ; uint32_t type_token_id (0x2000044)
		i32 29; uint32_t java_map_index (0x1d)
	}, ; 8
	%struct.TypeMapModuleEntry {
		i32 33554502, ; uint32_t type_token_id (0x2000046)
		i32 23; uint32_t java_map_index (0x17)
	}, ; 9
	%struct.TypeMapModuleEntry {
		i32 33554503, ; uint32_t type_token_id (0x2000047)
		i32 32; uint32_t java_map_index (0x20)
	}, ; 10
	%struct.TypeMapModuleEntry {
		i32 33554504, ; uint32_t type_token_id (0x2000048)
		i32 30; uint32_t java_map_index (0x1e)
	}, ; 11
	%struct.TypeMapModuleEntry {
		i32 33554505, ; uint32_t type_token_id (0x2000049)
		i32 47; uint32_t java_map_index (0x2f)
	}, ; 12
	%struct.TypeMapModuleEntry {
		i32 33554506, ; uint32_t type_token_id (0x200004a)
		i32 11; uint32_t java_map_index (0xb)
	}, ; 13
	%struct.TypeMapModuleEntry {
		i32 33554510, ; uint32_t type_token_id (0x200004e)
		i32 45; uint32_t java_map_index (0x2d)
	}, ; 14
	%struct.TypeMapModuleEntry {
		i32 33554511, ; uint32_t type_token_id (0x200004f)
		i32 27; uint32_t java_map_index (0x1b)
	}, ; 15
	%struct.TypeMapModuleEntry {
		i32 33554512, ; uint32_t type_token_id (0x2000050)
		i32 35; uint32_t java_map_index (0x23)
	}, ; 16
	%struct.TypeMapModuleEntry {
		i32 33554514, ; uint32_t type_token_id (0x2000052)
		i32 39; uint32_t java_map_index (0x27)
	}, ; 17
	%struct.TypeMapModuleEntry {
		i32 33554518, ; uint32_t type_token_id (0x2000056)
		i32 49; uint32_t java_map_index (0x31)
	}, ; 18
	%struct.TypeMapModuleEntry {
		i32 33554519, ; uint32_t type_token_id (0x2000057)
		i32 24; uint32_t java_map_index (0x18)
	}, ; 19
	%struct.TypeMapModuleEntry {
		i32 33554541, ; uint32_t type_token_id (0x200006d)
		i32 48; uint32_t java_map_index (0x30)
	}, ; 20
	%struct.TypeMapModuleEntry {
		i32 33554543, ; uint32_t type_token_id (0x200006f)
		i32 7; uint32_t java_map_index (0x7)
	}, ; 21
	%struct.TypeMapModuleEntry {
		i32 33554554, ; uint32_t type_token_id (0x200007a)
		i32 38; uint32_t java_map_index (0x26)
	}, ; 22
	%struct.TypeMapModuleEntry {
		i32 33554560, ; uint32_t type_token_id (0x2000080)
		i32 51; uint32_t java_map_index (0x33)
	}, ; 23
	%struct.TypeMapModuleEntry {
		i32 33554563, ; uint32_t type_token_id (0x2000083)
		i32 28; uint32_t java_map_index (0x1c)
	}, ; 24
	%struct.TypeMapModuleEntry {
		i32 33554565, ; uint32_t type_token_id (0x2000085)
		i32 19; uint32_t java_map_index (0x13)
	}, ; 25
	%struct.TypeMapModuleEntry {
		i32 33554566, ; uint32_t type_token_id (0x2000086)
		i32 46; uint32_t java_map_index (0x2e)
	}, ; 26
	%struct.TypeMapModuleEntry {
		i32 33554569, ; uint32_t type_token_id (0x2000089)
		i32 4; uint32_t java_map_index (0x4)
	}, ; 27
	%struct.TypeMapModuleEntry {
		i32 33554570, ; uint32_t type_token_id (0x200008a)
		i32 14; uint32_t java_map_index (0xe)
	}, ; 28
	%struct.TypeMapModuleEntry {
		i32 33554572, ; uint32_t type_token_id (0x200008c)
		i32 41; uint32_t java_map_index (0x29)
	}, ; 29
	%struct.TypeMapModuleEntry {
		i32 33554573, ; uint32_t type_token_id (0x200008d)
		i32 36; uint32_t java_map_index (0x24)
	}, ; 30
	%struct.TypeMapModuleEntry {
		i32 33554575, ; uint32_t type_token_id (0x200008f)
		i32 18; uint32_t java_map_index (0x12)
	}, ; 31
	%struct.TypeMapModuleEntry {
		i32 33554577, ; uint32_t type_token_id (0x2000091)
		i32 6; uint32_t java_map_index (0x6)
	}, ; 32
	%struct.TypeMapModuleEntry {
		i32 33554578, ; uint32_t type_token_id (0x2000092)
		i32 10; uint32_t java_map_index (0xa)
	}, ; 33
	%struct.TypeMapModuleEntry {
		i32 33554580, ; uint32_t type_token_id (0x2000094)
		i32 9; uint32_t java_map_index (0x9)
	}, ; 34
	%struct.TypeMapModuleEntry {
		i32 33554581, ; uint32_t type_token_id (0x2000095)
		i32 50; uint32_t java_map_index (0x32)
	}, ; 35
	%struct.TypeMapModuleEntry {
		i32 33554583, ; uint32_t type_token_id (0x2000097)
		i32 13; uint32_t java_map_index (0xd)
	}, ; 36
	%struct.TypeMapModuleEntry {
		i32 33554584, ; uint32_t type_token_id (0x2000098)
		i32 33; uint32_t java_map_index (0x21)
	}, ; 37
	%struct.TypeMapModuleEntry {
		i32 33554585, ; uint32_t type_token_id (0x2000099)
		i32 44; uint32_t java_map_index (0x2c)
	}, ; 38
	%struct.TypeMapModuleEntry {
		i32 33554587, ; uint32_t type_token_id (0x200009b)
		i32 15; uint32_t java_map_index (0xf)
	}, ; 39
	%struct.TypeMapModuleEntry {
		i32 33554588, ; uint32_t type_token_id (0x200009c)
		i32 16; uint32_t java_map_index (0x10)
	}, ; 40
	%struct.TypeMapModuleEntry {
		i32 33554589, ; uint32_t type_token_id (0x200009d)
		i32 5; uint32_t java_map_index (0x5)
	}, ; 41
	%struct.TypeMapModuleEntry {
		i32 33554590, ; uint32_t type_token_id (0x200009e)
		i32 40; uint32_t java_map_index (0x28)
	}, ; 42
	%struct.TypeMapModuleEntry {
		i32 33554593, ; uint32_t type_token_id (0x20000a1)
		i32 20; uint32_t java_map_index (0x14)
	}, ; 43
	%struct.TypeMapModuleEntry {
		i32 33554595, ; uint32_t type_token_id (0x20000a3)
		i32 37; uint32_t java_map_index (0x25)
	}, ; 44
	%struct.TypeMapModuleEntry {
		i32 33554596, ; uint32_t type_token_id (0x20000a4)
		i32 43; uint32_t java_map_index (0x2b)
	}, ; 45
	%struct.TypeMapModuleEntry {
		i32 33554597, ; uint32_t type_token_id (0x20000a5)
		i32 0; uint32_t java_map_index (0x0)
	}, ; 46
	%struct.TypeMapModuleEntry {
		i32 33554599, ; uint32_t type_token_id (0x20000a7)
		i32 22; uint32_t java_map_index (0x16)
	}, ; 47
	%struct.TypeMapModuleEntry {
		i32 33554600, ; uint32_t type_token_id (0x20000a8)
		i32 26; uint32_t java_map_index (0x1a)
	}, ; 48
	%struct.TypeMapModuleEntry {
		i32 33554601, ; uint32_t type_token_id (0x20000a9)
		i32 42; uint32_t java_map_index (0x2a)
	}, ; 49
	%struct.TypeMapModuleEntry {
		i32 33554613, ; uint32_t type_token_id (0x20000b5)
		i32 31; uint32_t java_map_index (0x1f)
	} ; 50
], align 4

@module1_managed_to_java_duplicates = internal dso_local constant [13 x %struct.TypeMapModuleEntry] [
	%struct.TypeMapModuleEntry {
		i32 33554495, ; uint32_t type_token_id (0x200003f)
		i32 34; uint32_t java_map_index (0x22)
	}, ; 0
	%struct.TypeMapModuleEntry {
		i32 33554513, ; uint32_t type_token_id (0x2000051)
		i32 35; uint32_t java_map_index (0x23)
	}, ; 1
	%struct.TypeMapModuleEntry {
		i32 33554520, ; uint32_t type_token_id (0x2000058)
		i32 49; uint32_t java_map_index (0x31)
	}, ; 2
	%struct.TypeMapModuleEntry {
		i32 33554564, ; uint32_t type_token_id (0x2000084)
		i32 28; uint32_t java_map_index (0x1c)
	}, ; 3
	%struct.TypeMapModuleEntry {
		i32 33554567, ; uint32_t type_token_id (0x2000087)
		i32 46; uint32_t java_map_index (0x2e)
	}, ; 4
	%struct.TypeMapModuleEntry {
		i32 33554571, ; uint32_t type_token_id (0x200008b)
		i32 14; uint32_t java_map_index (0xe)
	}, ; 5
	%struct.TypeMapModuleEntry {
		i32 33554574, ; uint32_t type_token_id (0x200008e)
		i32 36; uint32_t java_map_index (0x24)
	}, ; 6
	%struct.TypeMapModuleEntry {
		i32 33554576, ; uint32_t type_token_id (0x2000090)
		i32 18; uint32_t java_map_index (0x12)
	}, ; 7
	%struct.TypeMapModuleEntry {
		i32 33554579, ; uint32_t type_token_id (0x2000093)
		i32 10; uint32_t java_map_index (0xa)
	}, ; 8
	%struct.TypeMapModuleEntry {
		i32 33554582, ; uint32_t type_token_id (0x2000096)
		i32 50; uint32_t java_map_index (0x32)
	}, ; 9
	%struct.TypeMapModuleEntry {
		i32 33554586, ; uint32_t type_token_id (0x200009a)
		i32 44; uint32_t java_map_index (0x2c)
	}, ; 10
	%struct.TypeMapModuleEntry {
		i32 33554591, ; uint32_t type_token_id (0x200009f)
		i32 40; uint32_t java_map_index (0x28)
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
		i32 33554597, ; uint32_t type_token_id (0x20000a5)
		i32 47; uint32_t java_name_index (0x2f)
	}, ; 0
	%struct.TypeMapJava {
		i32 1, ; uint32_t module_index (0x1)
		i32 33554488, ; uint32_t type_token_id (0x2000038)
		i32 1; uint32_t java_name_index (0x1)
	}, ; 1
	%struct.TypeMapJava {
		i32 1, ; uint32_t module_index (0x1)
		i32 33554498, ; uint32_t type_token_id (0x2000042)
		i32 7; uint32_t java_name_index (0x7)
	}, ; 2
	%struct.TypeMapJava {
		i32 1, ; uint32_t module_index (0x1)
		i32 33554490, ; uint32_t type_token_id (0x200003a)
		i32 3; uint32_t java_name_index (0x3)
	}, ; 3
	%struct.TypeMapJava {
		i32 1, ; uint32_t module_index (0x1)
		i32 33554569, ; uint32_t type_token_id (0x2000089)
		i32 28; uint32_t java_name_index (0x1c)
	}, ; 4
	%struct.TypeMapJava {
		i32 1, ; uint32_t module_index (0x1)
		i32 33554589, ; uint32_t type_token_id (0x200009d)
		i32 42; uint32_t java_name_index (0x2a)
	}, ; 5
	%struct.TypeMapJava {
		i32 1, ; uint32_t module_index (0x1)
		i32 33554577, ; uint32_t type_token_id (0x2000091)
		i32 33; uint32_t java_name_index (0x21)
	}, ; 6
	%struct.TypeMapJava {
		i32 1, ; uint32_t module_index (0x1)
		i32 33554543, ; uint32_t type_token_id (0x200006f)
		i32 22; uint32_t java_name_index (0x16)
	}, ; 7
	%struct.TypeMapJava {
		i32 1, ; uint32_t module_index (0x1)
		i32 33554497, ; uint32_t type_token_id (0x2000041)
		i32 6; uint32_t java_name_index (0x6)
	}, ; 8
	%struct.TypeMapJava {
		i32 1, ; uint32_t module_index (0x1)
		i32 33554580, ; uint32_t type_token_id (0x2000094)
		i32 35; uint32_t java_name_index (0x23)
	}, ; 9
	%struct.TypeMapJava {
		i32 1, ; uint32_t module_index (0x1)
		i32 33554578, ; uint32_t type_token_id (0x2000092)
		i32 34; uint32_t java_name_index (0x22)
	}, ; 10
	%struct.TypeMapJava {
		i32 1, ; uint32_t module_index (0x1)
		i32 33554506, ; uint32_t type_token_id (0x200004a)
		i32 14; uint32_t java_name_index (0xe)
	}, ; 11
	%struct.TypeMapJava {
		i32 0, ; uint32_t module_index (0x0)
		i32 33554446, ; uint32_t type_token_id (0x200000e)
		i32 0; uint32_t java_name_index (0x0)
	}, ; 12
	%struct.TypeMapJava {
		i32 1, ; uint32_t module_index (0x1)
		i32 33554583, ; uint32_t type_token_id (0x2000097)
		i32 37; uint32_t java_name_index (0x25)
	}, ; 13
	%struct.TypeMapJava {
		i32 1, ; uint32_t module_index (0x1)
		i32 0, ; uint32_t type_token_id (0x0)
		i32 29; uint32_t java_name_index (0x1d)
	}, ; 14
	%struct.TypeMapJava {
		i32 1, ; uint32_t module_index (0x1)
		i32 33554587, ; uint32_t type_token_id (0x200009b)
		i32 40; uint32_t java_name_index (0x28)
	}, ; 15
	%struct.TypeMapJava {
		i32 1, ; uint32_t module_index (0x1)
		i32 33554588, ; uint32_t type_token_id (0x200009c)
		i32 41; uint32_t java_name_index (0x29)
	}, ; 16
	%struct.TypeMapJava {
		i32 1, ; uint32_t module_index (0x1)
		i32 33554489, ; uint32_t type_token_id (0x2000039)
		i32 2; uint32_t java_name_index (0x2)
	}, ; 17
	%struct.TypeMapJava {
		i32 1, ; uint32_t module_index (0x1)
		i32 33554575, ; uint32_t type_token_id (0x200008f)
		i32 32; uint32_t java_name_index (0x20)
	}, ; 18
	%struct.TypeMapJava {
		i32 1, ; uint32_t module_index (0x1)
		i32 33554565, ; uint32_t type_token_id (0x2000085)
		i32 26; uint32_t java_name_index (0x1a)
	}, ; 19
	%struct.TypeMapJava {
		i32 1, ; uint32_t module_index (0x1)
		i32 0, ; uint32_t type_token_id (0x0)
		i32 44; uint32_t java_name_index (0x2c)
	}, ; 20
	%struct.TypeMapJava {
		i32 1, ; uint32_t module_index (0x1)
		i32 33554491, ; uint32_t type_token_id (0x200003b)
		i32 4; uint32_t java_name_index (0x4)
	}, ; 21
	%struct.TypeMapJava {
		i32 1, ; uint32_t module_index (0x1)
		i32 33554599, ; uint32_t type_token_id (0x20000a7)
		i32 48; uint32_t java_name_index (0x30)
	}, ; 22
	%struct.TypeMapJava {
		i32 1, ; uint32_t module_index (0x1)
		i32 33554502, ; uint32_t type_token_id (0x2000046)
		i32 10; uint32_t java_name_index (0xa)
	}, ; 23
	%struct.TypeMapJava {
		i32 1, ; uint32_t module_index (0x1)
		i32 33554519, ; uint32_t type_token_id (0x2000057)
		i32 20; uint32_t java_name_index (0x14)
	}, ; 24
	%struct.TypeMapJava {
		i32 1, ; uint32_t module_index (0x1)
		i32 33554499, ; uint32_t type_token_id (0x2000043)
		i32 8; uint32_t java_name_index (0x8)
	}, ; 25
	%struct.TypeMapJava {
		i32 1, ; uint32_t module_index (0x1)
		i32 33554600, ; uint32_t type_token_id (0x20000a8)
		i32 49; uint32_t java_name_index (0x31)
	}, ; 26
	%struct.TypeMapJava {
		i32 1, ; uint32_t module_index (0x1)
		i32 33554511, ; uint32_t type_token_id (0x200004f)
		i32 16; uint32_t java_name_index (0x10)
	}, ; 27
	%struct.TypeMapJava {
		i32 1, ; uint32_t module_index (0x1)
		i32 33554563, ; uint32_t type_token_id (0x2000083)
		i32 25; uint32_t java_name_index (0x19)
	}, ; 28
	%struct.TypeMapJava {
		i32 1, ; uint32_t module_index (0x1)
		i32 33554500, ; uint32_t type_token_id (0x2000044)
		i32 9; uint32_t java_name_index (0x9)
	}, ; 29
	%struct.TypeMapJava {
		i32 1, ; uint32_t module_index (0x1)
		i32 33554504, ; uint32_t type_token_id (0x2000048)
		i32 12; uint32_t java_name_index (0xc)
	}, ; 30
	%struct.TypeMapJava {
		i32 1, ; uint32_t module_index (0x1)
		i32 33554613, ; uint32_t type_token_id (0x20000b5)
		i32 51; uint32_t java_name_index (0x33)
	}, ; 31
	%struct.TypeMapJava {
		i32 1, ; uint32_t module_index (0x1)
		i32 33554503, ; uint32_t type_token_id (0x2000047)
		i32 11; uint32_t java_name_index (0xb)
	}, ; 32
	%struct.TypeMapJava {
		i32 1, ; uint32_t module_index (0x1)
		i32 33554584, ; uint32_t type_token_id (0x2000098)
		i32 38; uint32_t java_name_index (0x26)
	}, ; 33
	%struct.TypeMapJava {
		i32 1, ; uint32_t module_index (0x1)
		i32 0, ; uint32_t type_token_id (0x0)
		i32 5; uint32_t java_name_index (0x5)
	}, ; 34
	%struct.TypeMapJava {
		i32 1, ; uint32_t module_index (0x1)
		i32 0, ; uint32_t type_token_id (0x0)
		i32 17; uint32_t java_name_index (0x11)
	}, ; 35
	%struct.TypeMapJava {
		i32 1, ; uint32_t module_index (0x1)
		i32 33554573, ; uint32_t type_token_id (0x200008d)
		i32 31; uint32_t java_name_index (0x1f)
	}, ; 36
	%struct.TypeMapJava {
		i32 1, ; uint32_t module_index (0x1)
		i32 33554595, ; uint32_t type_token_id (0x20000a3)
		i32 45; uint32_t java_name_index (0x2d)
	}, ; 37
	%struct.TypeMapJava {
		i32 1, ; uint32_t module_index (0x1)
		i32 33554554, ; uint32_t type_token_id (0x200007a)
		i32 23; uint32_t java_name_index (0x17)
	}, ; 38
	%struct.TypeMapJava {
		i32 1, ; uint32_t module_index (0x1)
		i32 33554514, ; uint32_t type_token_id (0x2000052)
		i32 18; uint32_t java_name_index (0x12)
	}, ; 39
	%struct.TypeMapJava {
		i32 1, ; uint32_t module_index (0x1)
		i32 0, ; uint32_t type_token_id (0x0)
		i32 43; uint32_t java_name_index (0x2b)
	}, ; 40
	%struct.TypeMapJava {
		i32 1, ; uint32_t module_index (0x1)
		i32 33554572, ; uint32_t type_token_id (0x200008c)
		i32 30; uint32_t java_name_index (0x1e)
	}, ; 41
	%struct.TypeMapJava {
		i32 1, ; uint32_t module_index (0x1)
		i32 33554601, ; uint32_t type_token_id (0x20000a9)
		i32 50; uint32_t java_name_index (0x32)
	}, ; 42
	%struct.TypeMapJava {
		i32 1, ; uint32_t module_index (0x1)
		i32 33554596, ; uint32_t type_token_id (0x20000a4)
		i32 46; uint32_t java_name_index (0x2e)
	}, ; 43
	%struct.TypeMapJava {
		i32 1, ; uint32_t module_index (0x1)
		i32 33554585, ; uint32_t type_token_id (0x2000099)
		i32 39; uint32_t java_name_index (0x27)
	}, ; 44
	%struct.TypeMapJava {
		i32 1, ; uint32_t module_index (0x1)
		i32 33554510, ; uint32_t type_token_id (0x200004e)
		i32 15; uint32_t java_name_index (0xf)
	}, ; 45
	%struct.TypeMapJava {
		i32 1, ; uint32_t module_index (0x1)
		i32 0, ; uint32_t type_token_id (0x0)
		i32 27; uint32_t java_name_index (0x1b)
	}, ; 46
	%struct.TypeMapJava {
		i32 1, ; uint32_t module_index (0x1)
		i32 33554505, ; uint32_t type_token_id (0x2000049)
		i32 13; uint32_t java_name_index (0xd)
	}, ; 47
	%struct.TypeMapJava {
		i32 1, ; uint32_t module_index (0x1)
		i32 33554541, ; uint32_t type_token_id (0x200006d)
		i32 21; uint32_t java_name_index (0x15)
	}, ; 48
	%struct.TypeMapJava {
		i32 1, ; uint32_t module_index (0x1)
		i32 33554518, ; uint32_t type_token_id (0x2000056)
		i32 19; uint32_t java_name_index (0x13)
	}, ; 49
	%struct.TypeMapJava {
		i32 1, ; uint32_t module_index (0x1)
		i32 33554581, ; uint32_t type_token_id (0x2000095)
		i32 36; uint32_t java_name_index (0x24)
	}, ; 50
	%struct.TypeMapJava {
		i32 1, ; uint32_t module_index (0x1)
		i32 33554560, ; uint32_t type_token_id (0x2000080)
		i32 24; uint32_t java_name_index (0x18)
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
], align 8

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
!llvm.module.flags = !{!0, !1, !7, !8, !9, !10}
!0 = !{i32 1, !"wchar_size", i32 4}
!1 = !{i32 7, !"PIC Level", i32 2}
!llvm.ident = !{!2}
!2 = !{!"Xamarin.Android remotes/origin/release/8.0.4xx @ 82d8938cf80f6d5fa6c28529ddfbdb753d805ab4"}
!3 = !{!4, !4, i64 0}
!4 = !{!"any pointer", !5, i64 0}
!5 = !{!"omnipotent char", !6, i64 0}
!6 = !{!"Simple C++ TBAA"}
!7 = !{i32 1, !"branch-target-enforcement", i32 0}
!8 = !{i32 1, !"sign-return-address", i32 0}
!9 = !{i32 1, !"sign-return-address-all", i32 0}
!10 = !{i32 1, !"sign-return-address-with-bkey", i32 0}
