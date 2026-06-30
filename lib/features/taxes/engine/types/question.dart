/// Adaptive interview primitives.
///
/// The interview collects a flat `Answers` record keyed by question id. A pure
/// `buildReturn(answers)` converts those answers into the canonical
/// TaxReturn2024 the engine consumes. Each question's `dependsOn` predicate runs
/// over the live answers, so the visible set recomputes on every change and
/// irrelevant questions simply disappear.
library;

/// One answer value: a string, number, or boolean.
typedef AnswerValue = Object;

/// Flat map of question id -> answer value.
typedef Answers = Map<String, AnswerValue>;

/// Input control type for a question.
/// One of "boolean" | "dollar" | "integer" | "select" | "text".
typedef InputType = String;

class QuestionOption {
  QuestionOption({required this.value, required this.label});

  String value;
  String label;
}

class Question {
  Question({
    required this.id,
    required this.sectionId,
    required this.text,
    this.helpText,
    required this.inputType,
    this.options,
    this.placeholder,
    this.dependsOn,
    this.sensitive,
    this.allowNegative,
  });

  String id;
  String sectionId;

  /// Plain-English label, phrased like TurboTax — not government-form language.
  String text;

  /// "Why we ask" + the IRS form/line this feeds.
  String? helpText;
  InputType inputType;
  List<QuestionOption>? options;
  String? placeholder;

  /// Pure predicate over the live answers — true means show this question.
  bool Function(Answers a)? dependsOn;

  /// SENSITIVE (SSN, bank) — never persisted to localStorage.
  bool? sensitive;

  /// Allow negative values (e.g. capital gains/losses).
  bool? allowNegative;
}

class Section {
  Section({
    required this.id,
    required this.title,
    this.description,
    this.icon,
    this.dependsOn,
  });

  String id;
  String title;
  String? description;

  /// lucide-react icon name.
  String? icon;

  /// Section-level gate — hidden entirely when false.
  bool Function(Answers a)? dependsOn;
}
