import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../core/auth/auth_service.dart';
import '../../core/config/app_config.dart';
import '../../core/design_system/design_system.dart';
import 'auth_scaffold.dart';
import 'sign_up_screen.dart';

enum _Step { email, password }

/// Two-step email → password sign-in, mirroring the web's sign-in flow, with a
/// native Google option.
class SignInScreen extends StatefulWidget {
  const SignInScreen({super.key});

  @override
  State<SignInScreen> createState() => _SignInScreenState();
}

class _SignInScreenState extends State<SignInScreen> {
  final _emailController = TextEditingController();
  final _passwordController = TextEditingController();
  _Step _step = _Step.email;
  bool _busy = false;
  String? _error;

  @override
  void dispose() {
    _emailController.dispose();
    _passwordController.dispose();
    super.dispose();
  }

  void _continue() {
    final email = _emailController.text.trim();
    if (email.isEmpty || !email.contains('@')) {
      setState(() => _error = 'Enter a valid email address.');
      return;
    }
    setState(() {
      _error = null;
      _step = _Step.password;
    });
  }

  Future<void> _signIn() async {
    if (_passwordController.text.isEmpty) {
      setState(() => _error = 'Enter your password.');
      return;
    }
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      await context.read<AuthService>().signIn(
            _emailController.text,
            _passwordController.text,
          );
      if (mounted) Navigator.of(context).pop();
    } on AuthFailure catch (e) {
      if (mounted) setState(() => _error = e.message);
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _google() async {
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      final ok = await context.read<AuthService>().signInWithGoogle();
      if (ok && mounted) Navigator.of(context).pop();
    } on AuthFailure catch (e) {
      if (mounted) setState(() => _error = e.message);
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return AuthScaffold(
      title: _step == _Step.email ? 'Welcome back' : 'Enter your password',
      description: _step == _Step.email
          ? 'Sign in to your FinnaCalc account.'
          : _emailController.text.trim(),
      error: _error,
      children: [
        if (_step == _Step.email) ..._emailStep() else ..._passwordStep(),
        const SizedBox(height: 16),
        Center(
          child: AuthTextLink(
            leading: "Don't have an account? ",
            link: 'Sign up',
            onTap: () => Navigator.of(context).pushReplacement(
              MaterialPageRoute(builder: (_) => const SignUpScreen()),
            ),
          ),
        ),
      ],
    );
  }

  List<Widget> _emailStep() {
    return [
      FCTextField(
        controller: _emailController,
        label: 'Email',
        hintText: 'you@example.com',
        keyboardType: TextInputType.emailAddress,
        textInputAction: TextInputAction.next,
        onSubmitted: (_) => _continue(),
      ),
      const SizedBox(height: 16),
      FCButton(
        label: 'Continue',
        fullWidth: true,
        loading: _busy,
        onPressed: _busy ? null : _continue,
      ),
      if (AppConfig.isGoogleConfigured) ...[
        const SizedBox(height: 16),
        const AuthOrDivider(),
        const SizedBox(height: 16),
        FCButton(
          label: 'Continue with Google',
          variant: FCButtonVariant.outline,
          fullWidth: true,
          loading: _busy,
          icon: const Icon(Icons.login),
          onPressed: _busy ? null : _google,
        ),
      ],
    ];
  }

  List<Widget> _passwordStep() {
    return [
      FCTextField(
        controller: _passwordController,
        label: 'Password',
        hintText: '••••••••',
        obscureText: true,
        autofocus: true,
        textInputAction: TextInputAction.done,
        onSubmitted: (_) => _signIn(),
      ),
      const SizedBox(height: 16),
      FCButton(
        label: 'Sign in',
        fullWidth: true,
        loading: _busy,
        onPressed: _busy ? null : _signIn,
      ),
      const SizedBox(height: 8),
      FCButton(
        label: 'Use a different email',
        variant: FCButtonVariant.ghost,
        fullWidth: true,
        onPressed: _busy
            ? null
            : () => setState(() {
                  _step = _Step.email;
                  _error = null;
                }),
      ),
    ];
  }
}
