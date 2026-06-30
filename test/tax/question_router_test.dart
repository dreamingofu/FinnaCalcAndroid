import 'package:flutter_test/flutter_test.dart';
import 'package:finnacalc/features/taxes/engine/types/question.dart';
import 'package:finnacalc/features/taxes/questions/question_bank.dart';
import 'package:finnacalc/features/taxes/questions/question_router.dart';

List<String> _ids(List<dynamic> xs) =>
    xs.map((x) => (x as dynamic).id as String).toList();

void main() {
  group('adaptive question router', () {
    test('hides gated sections until their life situation is checked', () {
      final base = getVisibleSections({});
      expect(_ids(base), isNot(contains('income-job')));
      expect(_ids(base), contains('about-you')); // always shown

      final withJob = getVisibleSections({'ls_job': true});
      expect(_ids(withJob), contains('income-job'));
    });

    test("shows the MFS 'lived apart' question only for MFS filers", () {
      final q = questionBank.firstWhere((x) => x.id == 'q_lived_apart');
      expect(isQuestionVisible(q, {'q_filing': 'single'}), false);
      expect(isQuestionVisible(q, {'q_filing': 'mfs'}), true);
    });

    test('reveals itemized fields only when the user opts to itemize', () {
      final noItemize = getQuestionsForSection('deductions', {});
      expect(_ids(noItemize), ['q_itemize']);
      final itemize = getQuestionsForSection('deductions', {'q_itemize': true});
      expect(_ids(itemize), contains('q_mortgage_interest'));
    });

    test(
        'reveals the early-withdrawal question only when there are taxable distributions',
        () {
      final q = questionBank.firstWhere((x) => x.id == 'q_retire_early');
      expect(isQuestionVisible(q, {'ls_retire': true}), false);
      expect(
        isQuestionVisible(q, {'ls_retire': true, 'q_retire_taxable': 5000}),
        true,
      );
    });

    test('prunes answers for questions that are no longer visible', () {
      // Wages entered, then the job life-situation is turned off.
      final Answers answers = {'ls_job': true, 'q_wages': 50000};
      expect(pruneHidden(answers)['q_wages'], 50000);

      final Answers turnedOff = {'ls_job': false, 'q_wages': 50000};
      final pruned = pruneHidden(turnedOff);
      expect(pruned['q_wages'], isNull);
      expect(pruned['ls_job'], false); // life-situation key is preserved
    });

    test('prunes itemized answers when the user switches back to standard', () {
      final Answers answers = {'q_itemize': false, 'q_mortgage_interest': 12000};
      expect(pruneHidden(answers)['q_mortgage_interest'], isNull);
    });
  });
}
