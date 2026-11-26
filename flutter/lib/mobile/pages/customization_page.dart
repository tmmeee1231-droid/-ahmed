import 'dart:io';

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
      _unlocked = _passwordController.text == 'manw1231\$\;
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
    await AppAppearance.reset();
    if (mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('تمت استعادة الخلفية الأصلية')),
      );
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
;
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
    await AppAppearance.reset();
    if (mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('تمت استعادة الخلفية الأصلية')),
      );
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
