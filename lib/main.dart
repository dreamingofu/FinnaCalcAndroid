import 'package:flutter/material.dart';

import 'app/app.dart';
import 'app/bootstrap.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  final authService = await bootstrap();
  runApp(FinnaCalcApp(authService: authService));
}
