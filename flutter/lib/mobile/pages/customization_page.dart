import 'dart:convert';
import 'dart:io';
import 'dart:typed_data';

import 'package:archive/archive.dart';
import 'package:file_picker/file_picker.dart';
import 'package:flutter/material.dart';
import 'package:image_picker/image_picker.dart';
import 'package:path_provider/path_provider.dart';

class AppAppearance {
  static final ValueNotifier<File?> background = ValueNotifier<File?>(null);
  static const _fileName = 'vanta_custom_background';

  static Future<void> load() async {
    try {
      final dir = await getApplicationDocumentsDirectory();
      final matches = dir.listSync().where((f) => f.path.contains(_fileName));
      background.value = matches.isEmpty ? null : File(matches.first.path);
    } catch (_) {
      background.value = null;
    }
  }

  static Future<void> chooseBackground() async {
    final picked = await ImagePicker().pickImage(source: ImageSource.gallery);
    if (picked == null) return;
    final dir = await getApplicationDocumentsDirectory();
    final source = File(picked.path);
    final extension = picked.path.contains('.')
        ? picked.path.substring(picked.path.lastIndexOf('.'))
        : '.jpg';
    final target = File('${dir.path}/$_fileName$extension');
    if (await target.exists()) await target.delete();
    await source.copy(target.path);
    background.value = target;
  }

  static Future<void> reset() async {
    final dir = await getApplicationDocumentsDirectory();
    for (final file in dir.listSync()) {
      if (file.path.contains(_fileName)) await file.delete();
    }
    background.value = null;
  }


  static Future<String?> exportCustomization() async {
    final archive = Archive();
    final file = background.value;
    String? backgroundName;
    if (file != null && await file.exists()) {
      final extension = file.path.substring(file.path.lastIndexOf('.')).toLowerCase();
      if (!RegExp(r'^\.(jpg|jpeg|png|webp) extends StatefulWidget {
  const CustomizationPage({super.key});

  @override
  State<CustomizationPage> createState() => _CustomizationPageState();
}

class _CustomizationPageState extends State<CustomizationPage> {
  final _passwordController = TextEditingController();
  bool _unlocked = false;
  bool _wrongPassword = false;
  bool _busy = false;

  @override
  void dispose() {
    _passwordController.dispose();
    super.dispose();
  }

  void _unlock() {
    setState(() {
      _unlocked = _passwordController.text == 'manw1231\$\$';
      _wrongPassword = !_unlocked;
    });
  }


  Future<void> _runTransfer(bool doExport) async {
    setState(() => _busy = true);
    try {
      if (doExport) {
        final path = await AppAppearance.exportCustomization();
        if (path != null && mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(content: Text('تم حفظ ملف التخصيص: $path')),
          );
        }
      } else {
        final imported = await AppAppearance.importCustomization();
        if (imported && mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            const SnackBar(content: Text('تم استيراد الخلفية بنجاح')),
          );
        }
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('تعذر تنفيذ العملية: $e')),
        );
      }
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _chooseBackground() async {
    setState(() => _busy = true);
    try {
      await AppAppearance.chooseBackground();
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('تعذر اختيار الصورة: $e')),
        );
      }
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _reset() async {
    try {
      await AppAppearance.reset();
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('تمت استعادة الخلفية الأصلية')),
        );
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('تعذر استعادة الخلفية: $e')),
        );
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    const accent = Color(0xFFD62839);
    return Scaffold(
      appBar: AppBar(title: const Text('تخصيص الشكل')),
      body: SafeArea(
        child: !_unlocked
            ? Center(
                child: SingleChildScrollView(
                  padding: const EdgeInsets.all(24),
                  child: ConstrainedBox(
                    constraints: const BoxConstraints(maxWidth: 420),
                    child: Column(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        const Icon(Icons.lock_outline, size: 58, color: accent),
                        const SizedBox(height: 16),
                        const Text('أدخل كلمة المرور لفتح إعدادات التخصيص'),
                        const SizedBox(height: 18),
                        TextField(
                          controller: _passwordController,
                          obscureText: true,
                          onSubmitted: (_) => _unlock(),
                          decoration: InputDecoration(
                            labelText: 'كلمة المرور',
                            errorText: _wrongPassword ? 'كلمة المرور غير صحيحة' : null,
                            border: const OutlineInputBorder(),
                          ),
                        ),
                        const SizedBox(height: 16),
                        SizedBox(
                          width: double.infinity,
                          child: ElevatedButton(
                            onPressed: _unlock,
                            child: const Text('دخول'),
                          ),
                        ),
                      ],
                    ),
                  ),
                ),
              )
            : ListView(
                padding: const EdgeInsets.all(18),
                children: [
                  const Text('خلفية التطبيق',
                      style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold)),
                  const SizedBox(height: 12),
                  ValueListenableBuilder<File?>(
                    valueListenable: AppAppearance.background,
                    builder: (context, file, _) => ClipRRect(
                      borderRadius: BorderRadius.circular(14),
                      child: Container(
                        height: 190,
                        color: Theme.of(context).colorScheme.surface,
                        alignment: Alignment.center,
                        child: file != null && file.existsSync()
                            ? Image.file(file, fit: BoxFit.cover, width: double.infinity)
                            : const Text('الخلفية الأصلية مفعّلة'),
                      ),
                    ),
                  ),
                  const SizedBox(height: 14),
                  ElevatedButton.icon(
                    onPressed: _busy ? null : _chooseBackground,
                    icon: const Icon(Icons.photo_library_outlined),
                    label: Text(_busy ? 'جارٍ فتح المعرض...' : 'اختيار صورة من المعرض'),
                  ),
                  OutlinedButton.icon(
                    onPressed: _busy ? null : _reset,
                    icon: const Icon(Icons.restart_alt),
                    label: const Text('استعادة الشكل الأصلي'),
                  ),
                  const SizedBox(height: 24),
                  const Divider(),
                  const Text('نقل التخصيصات بين الأجهزة',
                      style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold)),
                  const SizedBox(height: 8),
                  const Text('صدّر الخلفية الحالية في ملف ZIP، وأرسل الملف للجهاز الآخر ثم استورده من هذه الصفحة.'),
                  const SizedBox(height: 12),
                  ElevatedButton.icon(
                    onPressed: _busy ? null : () => _runTransfer(true),
                    icon: const Icon(Icons.file_upload_outlined),
                    label: const Text('تصدير ملف التخصيص'),
                  ),
                  OutlinedButton.icon(
                    onPressed: _busy ? null : () => _runTransfer(false),
                    icon: const Icon(Icons.file_download_outlined),
                    label: const Text('استيراد ملف تخصيص'),
                  ),
                  const SizedBox(height: 24),
                  const Divider(),
                  const SizedBox(height: 12),
                  const Text('أيقونة التطبيق',
                      style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold)),
                  const SizedBox(height: 8),
                  const Text(
                    'نقل ملف التخصيص الحالي ينقل الخلفية فقط. تغيير أيقونة المشغّل يحتاج تضمين الأيقونة داخل APK جديد.',
                  ),
                ],
              ),
      ),
    );
  }
}
).hasMatch(extension)) {
        throw Exception('صيغة الخلفية غير مدعومة');
      }
      final bytes = await file.readAsBytes();
      backgroundName = 'background$extension';
      archive.addFile(ArchiveFile(backgroundName, bytes.length, bytes));
    }
    final manifest = utf8.encode(jsonEncode({
      'format': 'vanta-customization',
      'version': 1,
      'background': backgroundName,
    }));
    archive.addFile(ArchiveFile('manifest.json', manifest.length, manifest));
    final zip = ZipEncoder().encode(archive);
    if (zip == null || zip.isEmpty) throw Exception('تعذر إنشاء ملف التخصيص');
    return FilePicker.platform.saveFile(
      dialogTitle: 'حفظ نسخة التخصيص',
      fileName: 'VANTA-customization.zip',
      type: FileType.custom,
      allowedExtensions: ['zip'],
      bytes: Uint8List.fromList(zip),
    );
  }

  static Future<bool> importCustomization() async {
    final result = await FilePicker.platform.pickFiles(
      type: FileType.custom,
      allowedExtensions: ['zip'],
      withData: true,
    );
    if (result == null || result.files.isEmpty) return false;
    final bytes = result.files.single.bytes;
    if (bytes == null || bytes.isEmpty) throw Exception('تعذر قراءة الملف');
    final archive = ZipDecoder().decodeBytes(bytes);
    final manifests = archive.files.where(
      (entry) => entry.isFile && entry.name == 'manifest.json',
    );
    if (manifests.isEmpty) throw Exception('ملف تخصيص VANTA غير صالح');
    final manifest = jsonDecode(utf8.decode(
      List<int>.from(manifests.first.content as List),
    ));
    if (manifest is! Map ||
        manifest['format'] != 'vanta-customization' ||
        manifest['version'] != 1) {
      throw Exception('إصدار ملف التخصيص غير مدعوم');
    }
    final dir = await getApplicationDocumentsDirectory();
    final name = manifest['background'];
    if (name == null) {
      await _removeBackgroundFiles(dir);
      background.value = null;
      return true;
    }
    if (name is! String ||
        !RegExp(r'^background\.(jpg|jpeg|png|webp) extends StatefulWidget {
  const CustomizationPage({super.key});

  @override
  State<CustomizationPage> createState() => _CustomizationPageState();
}

class _CustomizationPageState extends State<CustomizationPage> {
  final _passwordController = TextEditingController();
  bool _unlocked = false;
  bool _wrongPassword = false;
  bool _busy = false;

  @override
  void dispose() {
    _passwordController.dispose();
    super.dispose();
  }

  void _unlock() {
    setState(() {
      _unlocked = _passwordController.text == 'manw1231\$\$';
      _wrongPassword = !_unlocked;
    });
  }

  Future<void> _chooseBackground() async {
    setState(() => _busy = true);
    try {
      await AppAppearance.chooseBackground();
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('تعذر اختيار الصورة: $e')),
        );
      }
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _reset() async {
    try {
      await AppAppearance.reset();
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('تمت استعادة الخلفية الأصلية')),
        );
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('تعذر استعادة الخلفية: $e')),
        );
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    const accent = Color(0xFFD62839);
    return Scaffold(
      appBar: AppBar(title: const Text('تخصيص الشكل')),
      body: SafeArea(
        child: !_unlocked
            ? Center(
                child: SingleChildScrollView(
                  padding: const EdgeInsets.all(24),
                  child: ConstrainedBox(
                    constraints: const BoxConstraints(maxWidth: 420),
                    child: Column(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        const Icon(Icons.lock_outline, size: 58, color: accent),
                        const SizedBox(height: 16),
                        const Text('أدخل كلمة المرور لفتح إعدادات التخصيص'),
                        const SizedBox(height: 18),
                        TextField(
                          controller: _passwordController,
                          obscureText: true,
                          onSubmitted: (_) => _unlock(),
                          decoration: InputDecoration(
                            labelText: 'كلمة المرور',
                            errorText: _wrongPassword ? 'كلمة المرور غير صحيحة' : null,
                            border: const OutlineInputBorder(),
                          ),
                        ),
                        const SizedBox(height: 16),
                        SizedBox(
                          width: double.infinity,
                          child: ElevatedButton(
                            onPressed: _unlock,
                            child: const Text('دخول'),
                          ),
                        ),
                      ],
                    ),
                  ),
                ),
              )
            : ListView(
                padding: const EdgeInsets.all(18),
                children: [
                  const Text('خلفية التطبيق',
                      style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold)),
                  const SizedBox(height: 12),
                  ValueListenableBuilder<File?>(
                    valueListenable: AppAppearance.background,
                    builder: (context, file, _) => ClipRRect(
                      borderRadius: BorderRadius.circular(14),
                      child: Container(
                        height: 190,
                        color: Theme.of(context).colorScheme.surface,
                        alignment: Alignment.center,
                        child: file != null && file.existsSync()
                            ? Image.file(file, fit: BoxFit.cover, width: double.infinity)
                            : const Text('الخلفية الأصلية مفعّلة'),
                      ),
                    ),
                  ),
                  const SizedBox(height: 14),
                  ElevatedButton.icon(
                    onPressed: _busy ? null : _chooseBackground,
                    icon: const Icon(Icons.photo_library_outlined),
                    label: Text(_busy ? 'جارٍ فتح المعرض...' : 'اختيار صورة من المعرض'),
                  ),
                  OutlinedButton.icon(
                    onPressed: _busy ? null : _reset,
                    icon: const Icon(Icons.restart_alt),
                    label: const Text('استعادة الشكل الأصلي'),
                  ),
                  const SizedBox(height: 24),
                  const Divider(),
                  const SizedBox(height: 12),
                  const Text('أيقونة التطبيق',
                      style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold)),
                  const SizedBox(height: 8),
                  const Text(
                    'أندرويد لا يسمح بتبديل أيقونة التطبيق الرئيسية إلى صورة عشوائية من المعرض بعد التثبيت. يمكن استخدام أيقونات بديلة مدمجة داخل APK أو إنشاء اختصار مستقل بصورة تختارها.',
                  ),
                ],
              ),
      ),
    );
  }
}
).hasMatch(name)) {
      throw Exception('بيانات الخلفية غير صالحة');
    }
    final entries = archive.files.where((entry) => entry.isFile && entry.name == name);
    if (entries.isEmpty) throw Exception('صورة الخلفية غير موجودة');
    final imageBytes = List<int>.from(entries.first.content as List);
    if (imageBytes.isEmpty) throw Exception('صورة الخلفية فارغة');
    await _removeBackgroundFiles(dir);
    final extension = name.substring('background'.length);
    final target = File('\${dir.path}/\$_fileName$extension');
    await target.writeAsBytes(imageBytes, flush: true);
    background.value = target;
    return true;
  }

  static Future<void> _removeBackgroundFiles(Directory dir) async {
    for (final entity in dir.listSync()) {
      final name = entity.path.split(Platform.pathSeparator).last;
      if (entity is File && name.startsWith(_fileName)) {
        await entity.delete();
      }
    }
  }

}

class CustomizationPage extends StatefulWidget {
  const CustomizationPage({super.key});

  @override
  State<CustomizationPage> createState() => _CustomizationPageState();
}

class _CustomizationPageState extends State<CustomizationPage> {
  final _passwordController = TextEditingController();
  bool _unlocked = false;
  bool _wrongPassword = false;
  bool _busy = false;

  @override
  void dispose() {
    _passwordController.dispose();
    super.dispose();
  }

  void _unlock() {
    setState(() {
      _unlocked = _passwordController.text == 'manw1231\$\$';
      _wrongPassword = !_unlocked;
    });
  }

  Future<void> _chooseBackground() async {
    setState(() => _busy = true);
    try {
      await AppAppearance.chooseBackground();
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('تعذر اختيار الصورة: $e')),
        );
      }
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _reset() async {
    try {
      await AppAppearance.reset();
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('تمت استعادة الخلفية الأصلية')),
        );
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('تعذر استعادة الخلفية: $e')),
        );
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    const accent = Color(0xFFD62839);
    return Scaffold(
      appBar: AppBar(title: const Text('تخصيص الشكل')),
      body: SafeArea(
        child: !_unlocked
            ? Center(
                child: SingleChildScrollView(
                  padding: const EdgeInsets.all(24),
                  child: ConstrainedBox(
                    constraints: const BoxConstraints(maxWidth: 420),
                    child: Column(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        const Icon(Icons.lock_outline, size: 58, color: accent),
                        const SizedBox(height: 16),
                        const Text('أدخل كلمة المرور لفتح إعدادات التخصيص'),
                        const SizedBox(height: 18),
                        TextField(
                          controller: _passwordController,
                          obscureText: true,
                          onSubmitted: (_) => _unlock(),
                          decoration: InputDecoration(
                            labelText: 'كلمة المرور',
                            errorText: _wrongPassword ? 'كلمة المرور غير صحيحة' : null,
                            border: const OutlineInputBorder(),
                          ),
                        ),
                        const SizedBox(height: 16),
                        SizedBox(
                          width: double.infinity,
                          child: ElevatedButton(
                            onPressed: _unlock,
                            child: const Text('دخول'),
                          ),
                        ),
                      ],
                    ),
                  ),
                ),
              )
            : ListView(
                padding: const EdgeInsets.all(18),
                children: [
                  const Text('خلفية التطبيق',
                      style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold)),
                  const SizedBox(height: 12),
                  ValueListenableBuilder<File?>(
                    valueListenable: AppAppearance.background,
                    builder: (context, file, _) => ClipRRect(
                      borderRadius: BorderRadius.circular(14),
                      child: Container(
                        height: 190,
                        color: Theme.of(context).colorScheme.surface,
                        alignment: Alignment.center,
                        child: file != null && file.existsSync()
                            ? Image.file(file, fit: BoxFit.cover, width: double.infinity)
                            : const Text('الخلفية الأصلية مفعّلة'),
                      ),
                    ),
                  ),
                  const SizedBox(height: 14),
                  ElevatedButton.icon(
                    onPressed: _busy ? null : _chooseBackground,
                    icon: const Icon(Icons.photo_library_outlined),
                    label: Text(_busy ? 'جارٍ فتح المعرض...' : 'اختيار صورة من المعرض'),
                  ),
                  OutlinedButton.icon(
                    onPressed: _busy ? null : _reset,
                    icon: const Icon(Icons.restart_alt),
                    label: const Text('استعادة الشكل الأصلي'),
                  ),
                  const SizedBox(height: 24),
                  const Divider(),
                  const SizedBox(height: 12),
                  const Text('أيقونة التطبيق',
                      style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold)),
                  const SizedBox(height: 8),
                  const Text(
                    'أندرويد لا يسمح بتبديل أيقونة التطبيق الرئيسية إلى صورة عشوائية من المعرض بعد التثبيت. يمكن استخدام أيقونات بديلة مدمجة داخل APK أو إنشاء اختصار مستقل بصورة تختارها.',
                  ),
                ],
              ),
      ),
    );
  }
}
