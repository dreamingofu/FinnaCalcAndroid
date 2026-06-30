import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../core/auth/auth_service.dart';
import '../../core/config/app_config.dart';
import '../../core/design_system/design_system.dart';
import 'auth_scaffold.dart';
import 'sign_in_screen.dart';

class SignUpScreen extends StatefulWidget {
  const SignUpScreen({super.key});

  @override
  State<SignUpScreen> createState() => _SignUpScreenState();
}

class _SignUpScreenState extends State<SignUpScreen> {
  final _nameController = TextEditingController();
  final _emailController = TextEditingController();
  final _passwordController = TextEditingController();
  bool _busy = false;
  bool _needsConfirmation = false;
  String? _error;

  @override
  void dispose() {
    _nameController.dispose();
    _emailController.dispose();
    _passwordController.dispose();
    super.dispose();
  }

  Future<void> _signUp() async {
    final name = _nameController.text.trim();
    final email = _emailController.text.trim();
    final password = _passwordController.text;
    if (name.isEmpty) {
      setState(() => _error = 'Enter your name.');
      return;
    }
    if (!email.contains('@')) {
      setState(() => _error = 'Enter a valid email address.');
      return;
    }
    if (password.length < 6) {
      setState(() => _error = 'Password must be at least 6 characters.');
      return;
    }
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      final result =
          await context.read<AuthService>().signUp(email, password, name);
      if (!mounted) return;
      if (result.needsConfirmation) {
        setState(() => _needsConfirmation = true);
      } else {
        Navigator.of(context).pop();
      }
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
    if (_needsConfirmation) {
      return AuthScaffold(
        title: 'Check your email',
        description:
            'We sent a confirmation link to ${_emailController.text.trim()}. '
            'Confirm it, then sign in.',
        children: [
          FCButton(
            label: 'Back to sign in',
            fullWidth: true,
            onPressed: () => Navigator.of(context).pushReplacement(
              MaterialPageRoute(builder: (_) => const SignInScreen()),
            ),
          ),
        ],
      );
    }

    return AuthScaffold(
      title: 'Create your account',
      description: 'Start using FinnaCalc for free.',
      error: _error,
      children: [
        FCTextField(
          controller: _nameController,
          label: 'Name',
          hintText: 'Jane Doe',
          textInputAction: TextInputAction.next,
          textCapitalization: TextCapitalization.words,
        ),
        const SizedBox(height: 16),
        FCTextField(
          controller: _emailController,
          label: 'Email',
          hintText: 'you@example.com',
          keyboardType: TextInputType.emailAddress,
          textInputAction: TextInputAction.next,
        ),
        const SizedBox(height: 16),
        FCTextField(
          controller: _passwordController,
          label: 'Password',
          hintText: 'At least 6 characters',
          obscureText: true,
          textInputAction: TextInputAction.done,
          onSubmitted: (_) => _signUp(),
        ),
        const SizedBox(height: 16),
        FCButton(
          label: 'Create account',
          fullWidth: true,
          loading: _busy,
          onPressed: _busy ? null : _signUp,
        ),
        if (AppConfig.isGoogleConfigured) ...[
          const SizedBox(height: 16),
          const AuthOrDivider(label: 'Or sign up with'),
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
        const SizedBox(height: 16),
        Center(
          child: AuthTextLink(
            leading: 'Already have an account? ',
            link: 'Sign in',
            onTap: () => Navigator.of(context).pushReplacement(
              MaterialPageRoute(builder: (_) => const SignInScreen()),
            ),
          ),
        ),
      ],
    );
  }
}
