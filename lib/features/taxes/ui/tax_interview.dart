import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../../core/design_system/design_system.dart';
import '../../../shared/widgets/result_widgets.dart';
import '../engine/types/question.dart';
import '../questions/question_router.dart';
import '../tax_controller.dart';
import 'filing_screen.dart';
import 'icons.dart';
import 'life_situations.dart';
import 'question_card.dart';
import 'refund_meter.dart';
import 'review_screen.dart';

/// Phases of the adaptive interview.
enum _Phase { life, sections, review, filing }

/// The adaptive tax interview — a phase machine
/// (life situations -> sections -> review -> filing). Mirrors `ui/TaxInterview.tsx`.
class TaxInterview extends StatefulWidget {
  const TaxInterview({super.key});

  @override
  State<TaxInterview> createState() => _TaxInterviewState();
}

class _TaxInterviewState extends State<TaxInterview> {
  _Phase _phase = _Phase.life;
  int _sectionIndex = 0;

  /// Section ids the user has reached — drives [getProgress].
  final Set<String> _visited = {};

  void _markVisited(List<Section> visible, int index) {
    if (index >= 0 && index < visible.length) {
      _visited.add(visible[index].id);
    }
  }

  void _goToSection(List<Section> visible, String id) {
    final idx = visible.indexWhere((s) => s.id == id);
    setState(() {
      _sectionIndex = idx >= 0 ? idx : 0;
      _phase = _Phase.sections;
      _markVisited(visible, _sectionIndex);
    });
  }

  void _next(List<Section> visible, int clampedIndex) {
    setState(() {
      if (clampedIndex < visible.length - 1) {
        _sectionIndex = clampedIndex + 1;
        _markVisited(visible, _sectionIndex);
      } else {
        _phase = _Phase.review;
      }
    });
  }

  void _back(int clampedIndex) {
    setState(() {
      if (clampedIndex > 0) {
        _sectionIndex = clampedIndex - 1;
      } else {
        _phase = _Phase.life;
      }
    });
  }

  Future<void> _confirmReset(TaxController ctrl) async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (dialogContext) {
        final c = dialogContext.colors;
        return AlertDialog(
          backgroundColor: c.popover,
          title: Text('Start over?',
              style: TextStyle(color: c.popoverForeground)),
          content: Text(
            "This clears every answer you've entered and resets your estimate. "
            "This can't be undone.",
            style:
                TextStyle(fontSize: FCFontSizes.sm, color: c.mutedForeground),
          ),
          actions: [
            FCButton(
              label: 'Keep my answers',
              variant: FCButtonVariant.outline,
              size: FCButtonSize.sm,
              onPressed: () => Navigator.of(dialogContext).pop(false),
            ),
            FCButton(
              label: 'Start over',
              variant: FCButtonVariant.destructive,
              size: FCButtonSize.sm,
              onPressed: () => Navigator.of(dialogContext).pop(true),
            ),
          ],
        );
      },
    );
    if (confirmed == true) {
      ctrl.reset();
      setState(() {
        _phase = _Phase.life;
        _sectionIndex = 0;
        _visited.clear();
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    final c = context.colors;
    final ctrl = context.watch<TaxController>();
    final answers = ctrl.answers;
    final result = ctrl.result;

    final visible = getVisibleSections(answers);
    final clampedIndex =
        visible.isEmpty ? 0 : _sectionIndex.clamp(0, visible.length - 1);
    final currentSection = visible.isEmpty ? null : visible[clampedIndex];
    final currentQuestions = currentSection == null
        ? const <Question>[]
        : getQuestionsForSection(currentSection.id, answers);

    final progress = getProgress(_visited.toList(), answers);

    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      mainAxisSize: MainAxisSize.min,
      children: [
        // Header
        Row(
          mainAxisAlignment: MainAxisAlignment.end,
          children: [
            const FCBadge('IRS-accurate', variant: FCBadgeVariant.secondary),
            const SizedBox(width: 8),
            FCButton(
              label: 'Start over',
              variant: FCButtonVariant.ghost,
              size: FCButtonSize.sm,
              icon: const Icon(Icons.restart_alt),
              onPressed: () => _confirmReset(ctrl),
            ),
          ],
        ),
        if (_phase != _Phase.life) ...[
          const SizedBox(height: 16),
          FCProgressBar(value: progress, color: c.primary, height: 8),
        ],
        const SizedBox(height: 24),

        // Live meter (hidden on the filing step) — shown prominently above.
        if (_phase != _Phase.filing) ...[
          RefundMeter(result: result),
          if (_phase == _Phase.sections && visible.isNotEmpty) ...[
            const SizedBox(height: 12),
            Text(
              'Step ${clampedIndex + 1} of ${visible.length}',
              textAlign: TextAlign.center,
              style:
                  TextStyle(fontSize: FCFontSizes.xs, color: c.mutedForeground),
            ),
          ],
          const SizedBox(height: 24),
        ],

        // Phase body
        if (_phase == _Phase.life)
          LifeSituations(
            onContinue: () => setState(() {
              _phase = _Phase.sections;
              _sectionIndex = 0;
              final v = getVisibleSections(ctrl.answers);
              _markVisited(v, 0);
            }),
          )
        else if (_phase == _Phase.sections && currentSection != null) ...[
          FCCard(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              mainAxisSize: MainAxisSize.min,
              children: [
                FCCardHeader(children: [
                  Row(
                    children: [
                      Icon(taxIconFor(currentSection.icon),
                          size: 20, color: c.primary),
                      const SizedBox(width: 8),
                      Expanded(child: FCCardTitle(currentSection.title)),
                    ],
                  ),
                  if (currentSection.description != null)
                    FCCardDescription(currentSection.description!),
                ]),
                FCCardContent(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.stretch,
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      if (currentQuestions.isEmpty)
                        Text(
                          'Nothing to enter here based on your answers — '
                          'continue.',
                          style: TextStyle(
                            fontSize: FCFontSizes.sm,
                            color: c.mutedForeground,
                          ),
                        )
                      else
                        for (var i = 0; i < currentQuestions.length; i++) ...[
                          if (i > 0) const SizedBox(height: 12),
                          QuestionCard(
                            key: ValueKey(currentQuestions[i].id),
                            question: currentQuestions[i],
                            value: answers[currentQuestions[i].id],
                            onChanged: (v) =>
                                ctrl.setAnswer(currentQuestions[i].id, v),
                          ),
                        ],
                    ],
                  ),
                ),
              ],
            ),
          ),
          const SizedBox(height: 16),
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              FCButton(
                label: 'Back',
                variant: FCButtonVariant.outline,
                icon: const Icon(Icons.arrow_back),
                onPressed: () => _back(clampedIndex),
              ),
              FCButton(
                label: clampedIndex < visible.length - 1 ? 'Next' : 'Review',
                trailingIcon: const Icon(Icons.arrow_forward),
                onPressed: () => _next(visible, clampedIndex),
              ),
            ],
          ),
        ] else if (_phase == _Phase.review)
          ReviewScreen(
            result: result,
            answers: answers,
            sections: visible,
            onEdit: (id) => _goToSection(visible, id),
            onFile: () => setState(() => _phase = _Phase.filing),
          )
        else if (_phase == _Phase.filing)
          FilingScreen(
            result: result,
            onBack: () => setState(() => _phase = _Phase.review),
          ),
      ],
    );
  }
}
