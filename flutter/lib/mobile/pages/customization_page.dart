import 'dart:convert';
import 'dart:io';
import 'package:archive/archive.dart';
import 'package:file_picker/file_picker.dart';
import 'package:flutter/material.dart';
import 'package:image_picker/image_picker.dart';
import 'package:path_provider/path_provider.dart';

class AppAppearance {
  static final ValueNotifier<File?> background = ValueNotifier<File?>(null);
  static const String _fileName = 'vanta_custom_background';

  static Future<void> load() async {
    try {
      final dir = await getApplicationDocumentsDirectory();
      final files = dir.listSync().whereType<File>().where((f) =>
          f.uri.pathSegments.last.startsWith(_fileName)).toList();
      background.value = files.isEmpty ? null : files.first;
    } catch (_) {
      background.value = null;
    }
  }

  static String _extension(String path) {
    final dot = path.lastIndexOf('.');
    if (dot < 0) return '.jpg';
    final ext = path.substring(dot).toLowerCase();
    return RegExp(r'^\.(jpg|jpeg|png|webp)$').hasMatch(ext) ? ext : '.jpg';
  }

  static Future<void> chooseBackground() async {
    final picked = await ImagePicker().pickImage(source: ImageSource.gallery);
    if (picked == null) return;
    final dir = await getApplicationDocumentsDirectory();
    await _removeBackgroundFiles(dir);
    final target = File('\${dir.path}/\$_fileName\${_extension(picked.path)}');
    await File(picked.path).copy(target.path);
    background.value = target;
  }

  static Future<void> reset() async {
    final dir = await getApplicationDocumentsDirectory();
    await _removeBackgroundFiles(dir);
    background.value = null;
  }

  static Future<void> _removeBackgroundFiles(Directory dir) async {
    for (final entity in dir.listSync()) {
      if (entity is File &&
          entity.uri.pathSegments.last.startsWith(_fileName)) {
        await entity.delete();
      }
    }
  }

  static Future<String?> exportCustomization() async {
    final archive = Archive();
    String? backgroundName;
    final file = background.value;
    if (file != null && await file.exists()) {
      final bytes = await file.readAsBytes();
      backgroundName = 'background\${_extension(file.path)}';
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
      bytes: zip,
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
    if (bytes == null || bytes.isEmpty || bytes.length > 30 * 1024 * 1024) {
      throw Exception('ملف التخصيص فارغ أو حجمه غير صالح');
    }
    final archive = ZipDecoder().decodeBytes(bytes);
    final manifests = archive.files.where(
        (entry) => entry.isFile && entry.name == 'manifest.json').toList();
    if (manifests.length != 1) throw Exception('ملف تخصيص VANTA غير صالح');
    final manifestBytes = List<int>.from(manifests.single.content as List);
    final manifest = jsonDecode(utf8.decode(manifestBytes));
    if (manifest is! Map ||
        manifest['format'] != 'vanta-customization' ||
        manifest['version'] != 1) {
      throw Exception('إصدار ملف التخصيص غير مدعوم');
    }

    final name = manifest['background'];
    final dir = await getApplicationDocumentsDirectory();
    if (name == null) {
      await _removeBackgroundFiles(dir);
      background.value = null;
      return true;
    }
    if (name is! String ||
        !RegExp(r'^background\.(jpg|jpeg|png|webp)$').hasMatch(name)) {
      throw Exception('بيانات الخلفية غير صالحة');
    }
    final matches = archive.files
        .where((entry) => entry.isFile && entry.name == name).toList();
    if (matches.length != 1) throw Exception('صورة الخلفية غير موجودة');
    final imageBytes = List<int>.from(matches.single.content as List);
    if (imageBytes.isEmpty || imageBytes.length > 25 * 1024 * 1024) {
      throw Exception('حجم صورة الخلفية غير صالح');
    }

    final ext = name.substring('background'.length);
    final target = File('\${dir.path}/\$_fileName$ext');
    final temp = File('\${target.path}.tmp');
    await temp.writeAsBytes(imageBytes, flush: true);
    await _removeBackgroundFiles(dir);
    await temp.rename(target.path);
    background.value = target;
    return true;
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

  Future<void> _transfer(bool export) async {
    setState(() => _busy = true);
    try {
      if (export) {
        final path = await AppAppearance.exportCustomization();
        if (path != null && mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
              SnackBar(content: Text('تم حفظ ملف التخصيص: $path')));
        }
      } else {
        final ok = await AppAppearance.importCustomization();
        if (ok && mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
              const SnackBar(content: Text('تم استيراد الخلفية بنجاح')));
        }
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(content: Text('تعذر تنفيذ العملية: $e')));
      }
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _choose() async {
    setState(() => _busy = true);
    try {
      await AppAppearance.chooseBackground();
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(content: Text('تعذر اختيار الصورة: $e')));
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
            const SnackBar(content: Text('تمت استعادة الخلفية الأصلية')));
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(content: Text('تعذر استعادة الخلفية: $e')));
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
                    child: Column(mainAxisSize: MainAxisSize.min, children: [
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
                          errorText:
                              _wrongPassword ? 'كلمة المرور غير صحيحة' : null,
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
                    ]),
                  ),
                ),
              )
            : ListView(padding: const EdgeInsets.all(18), children: [
                const Text('خلفية التطبيق',
                    style:
                        TextStyle(fontSize: 18, fontWeight: FontWeight.bold)),
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
                          ? Image.file(file,
                              fit: BoxFit.cover, width: double.infinity)
                          : const Text('الخلفية الأصلية مفعّلة'),
                    ),
                  ),
                ),
                const SizedBox(height: 14),
                ElevatedButton.icon(
                  onPressed: _busy ? null : _choose,
                  icon: const Icon(Icons.photo_library_outlined),
                  label: const Text('اختيار صورة من المعرض'),
                ),
                OutlinedButton.icon(
                  onPressed: _busy ? null : _reset,
                  icon: const Icon(Icons.restart_alt),
                  label: const Text('استعادة الشكل الأصلي'),
                ),
                const SizedBox(height: 24),
                const Divider(),
                const Text('نقل التخصيصات بين الأجهزة',
                    style:
                        TextStyle(fontSize: 18, fontWeight: FontWeight.bold)),
                const SizedBox(height: 8),
                const Text(
                    'صدّر الخلفية الحالية في ملف ZIP، وأرسل الملف للجهاز الآخر ثم استورده من هذه الصفحة.'),
                const SizedBox(height: 12),
                ElevatedButton.icon(
                  onPressed: _busy ? null : () => _transfer(true),
                  icon: const Icon(Icons.file_upload_outlined),
                  label: const Text('تصدير ملف التخصيص'),
                ),
                OutlinedButton.icon(
                  onPressed: _busy ? null : () => _transfer(false),
                  icon: const Icon(Icons.file_download_outlined),
                  label: const Text('استيراد ملف تخصيص'),
                ),
                const SizedBox(height: 24),
                const Divider(),
                const Text('أيقونة التطبيق',
                    style:
                        TextStyle(fontSize: 18, fontWeight: FontWeight.bold)),
                const SizedBox(height: 8),
                const Text(
                    'ملف النقل الحالي يشمل الخلفية فقط. تغيير أيقونة المشغّل يتطلب تضمين الأيقونة في APK جديد.'),
              ]),
      ),
    );
  }
}
