/// Pure adaptive-interview router. All functions are deterministic over the live
/// answers, so the UI can recompute the visible set on every change.
library;

import '../engine/types/question.dart';
import 'question_bank.dart';
import 'sections.dart';

final Map<String, Section> _sectionById = {
  for (final s in sections) s.id: s,
};

/// Is a single question currently visible (its section shows AND its own gate
/// passes)?
bool isQuestionVisible(Question q, Answers a) {
  final section = _sectionById[q.sectionId];
  if (section?.dependsOn != null && !section!.dependsOn!(a)) return false;
  return q.dependsOn == null || q.dependsOn!(a);
}

/// Sections that currently have at least one... well, that pass their gate.
List<Section> getVisibleSections(Answers a) {
  return sections.where((s) => s.dependsOn == null || s.dependsOn!(a)).toList();
}

/// Visible questions for a given section, in bank order.
List<Question> getQuestionsForSection(String sectionId, Answers a) {
  return questionBank
      .where((q) => q.sectionId == sectionId && isQuestionVisible(q, a))
      .toList();
}

/// All visible questions across all visible sections.
List<Question> getVisibleQuestions(Answers a) {
  return questionBank.where((q) => isQuestionVisible(q, a)).toList();
}

/// Remove answers whose questions are no longer visible (e.g. the user
/// unchecked a life situation, or switched filing status away from MFS). This
/// prevents hidden, stale values from polluting the calculation. Life-situation
/// toggles (ls_*) and any non-bank keys are preserved. Returns a new answers
/// object.
Answers pruneHidden(Answers a) {
  final Answers next = {};
  final bankIds = {for (final q in questionBank) q.id};
  for (final entry in a.entries) {
    final key = entry.key;
    final value = entry.value;
    if (!bankIds.contains(key)) {
      next[key] = value; // ls_* and other non-question keys are kept
      continue;
    }
    final q = questionBank.firstWhere((x) => x.id == key);
    if (isQuestionVisible(q, a)) next[key] = value;
  }
  return next;
}

/// Progress over the visible sections (0–100), given how many have been
/// visited.
double getProgress(List<String> visitedSectionIds, Answers a) {
  final visible = getVisibleSections(a);
  if (visible.isEmpty) return 0;
  final visited =
      visible.where((s) => visitedSectionIds.contains(s.id)).length;
  return (visited / visible.length * 100).round().toDouble();
}
