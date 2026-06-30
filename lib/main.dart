import 'package:flutter/material.dart';

import 'app/app.dart';
import 'app/bootstrap.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  final services = await bootstrap();
  runApp(FinnaCalcApp(authService: services.auth, apiClient: services.api));
}
