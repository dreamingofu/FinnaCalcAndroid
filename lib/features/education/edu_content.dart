// Shared catalog of education content (video lessons + reading resources) plus
// a small relevance-ranked search used by the education page. Keeping the data
// here lets both the search and the browse views use the same source of truth.
//
// Ported verbatim from the web app's `lib/education-content.ts`.

/// A single piece of education content (a video or an article).
class EduItem {
  const EduItem({required this.title, required this.url});

  final String title;
  final String url;
}

/// A top-level education topic ({ id, name }).
class EduTopic {
  const EduTopic({required this.id, required this.name});

  final String id;
  final String name;
}

const List<EduTopic> eduTopics = [
  EduTopic(id: 'credit', name: 'Credit & Debt'),
  EduTopic(id: 'investing', name: 'Investing'),
  EduTopic(id: 'budgeting', name: 'Budgeting'),
  EduTopic(id: 'retirement', name: 'Retirement'),
  EduTopic(id: 'taxes', name: 'Tax Planning'),
];

const Map<String, List<EduItem>> videoLessons = {
  'credit': [
    EduItem(
      title: 'What Is a Credit Score?',
      url: 'https://www.youtube.com/watch?v=jwML94IOW0s',
    ),
    EduItem(
      title: 'What Can Change Your Credit Score?',
      url: 'https://www.youtube.com/watch?v=IZN5IT28iHo',
    ),
    EduItem(
      title: 'Understanding Loans and Debt',
      url: 'https://www.youtube.com/watch?v=E2dzSPOhUOI',
    ),
    EduItem(
      title: 'Good Debt vs. Bad Debt',
      url: 'https://www.youtube.com/watch?v=MFCdA2vGVh4',
    ),
    EduItem(
      title: 'What Is APR and Why It Matters',
      url: 'https://www.youtube.com/watch?v=MqqXTrEEZ7Y',
    ),
    EduItem(
      title: 'Understanding Your FICO Score',
      url: 'https://www.youtube.com/watch?v=8AtM1R9NmwM',
    ),
  ],
  'investing': [
    EduItem(
      title: 'What Are Stocks?',
      url: 'https://www.youtube.com/watch?v=98qfFzqDKR8',
    ),
    EduItem(
      title: "Bonds vs. Stocks: What's the Difference?",
      url: 'https://www.youtube.com/watch?v=rs1md3e4aYU',
    ),
    EduItem(
      title: 'Understanding Risk and Return',
      url: 'https://www.youtube.com/watch?v=7mo167ohvJw',
    ),
    EduItem(
      title: "A Beginner's Guide to Investing",
      url: 'https://www.youtube.com/watch?v=8_iWSsoiNXs',
    ),
    EduItem(
      title: 'Index Funds vs. Mutual Funds vs. ETFs',
      url: 'https://www.youtube.com/watch?v=ugBs333NhbI',
    ),
  ],
  'retirement': [
    EduItem(
      title: 'What Is a 401(k)?',
      url: 'https://www.youtube.com/watch?v=d8rNitoPZeo',
    ),
    EduItem(
      title: 'An Introduction to Traditional IRAs',
      url: 'https://www.youtube.com/watch?v=UV8kgqk_DAY',
    ),
    EduItem(
      title: 'The Power of a Roth IRA',
      url: 'https://www.youtube.com/watch?v=Xd8VXDqXtkE',
    ),
    EduItem(
      title: 'Managing Your 401(k) When You Change Jobs',
      url: 'https://www.youtube.com/watch?v=PLZHTIrazF8',
    ),
  ],
  'budgeting': [
    EduItem(
      title: 'How to Budget Your Paycheck',
      url: 'https://www.youtube.com/watch?v=5tQuez0kbOY',
    ),
    EduItem(
      title: 'How to Stop Living Paycheck to Paycheck',
      url: 'https://www.youtube.com/watch?v=NSpMFtcXxcc',
    ),
    EduItem(
      title: 'How to Manage Your Money (The 50/30/20 Rule)',
      url: 'https://www.youtube.com/watch?v=HQzoZfc3GwQ',
    ),
    EduItem(
      title: 'How to Manage Your Money (The 70/20/10 Rule)',
      url: 'https://www.youtube.com/watch?v=HkNPZVu-jZM',
    ),
    EduItem(
      title: "A Beginner's Guide to Paying Off Debt",
      url: 'https://www.youtube.com/watch?v=_LdpjN2oDNo',
    ),
  ],
  'taxes': [
    EduItem(
      title: 'What Are Taxes?',
      url: 'https://www.youtube.com/watch?v=kdfk22Ck4nM',
    ),
    EduItem(
      title: 'How Tax Brackets Work',
      url: 'https://www.youtube.com/watch?v=AhgR3X--bbY',
    ),
    EduItem(
      title: 'An Introduction to Tax Deductions',
      url: 'https://www.youtube.com/watch?v=GypHy3gnG5E',
    ),
    EduItem(
      title: 'Understanding Tax Credits',
      url: 'https://www.youtube.com/watch?v=4gYvlMwvdnw',
    ),
    EduItem(
      title: 'A Guide to Common Tax Forms (Part 1)',
      url: 'https://www.youtube.com/watch?v=boklbFhF8l8',
    ),
    EduItem(
      title: 'A Guide to Common Tax Forms (Part 2)',
      url: 'https://www.youtube.com/watch?v=W1562KoBExA',
    ),
  ],
};

const Map<String, List<EduItem>> readingResources = {
  'credit': [
    EduItem(
      title: 'An Introduction to Credit and Loans',
      url:
          'https://www.khanacademy.org/college-careers-more/financial-literacy/xa6995ea67a8e9fdd:loans-and-debt/xa6995ea67a8e9fdd:borrowing-money/a/loans-and-credit',
    ),
    EduItem(
      title: 'How to Raise Your Credit Score',
      url:
          'https://www.khanacademy.org/college-careers-more/financial-literacy/xa6995ea67a8e9fdd:consumer-credit/xa6995ea67a8e9fdd:credit-score/a/how-do-i-raise-my-credit-score',
    ),
  ],
  'investing': [
    EduItem(
      title: 'How to Invest with Confidence',
      url:
          'https://www.investopedia.com/articles/basics/11/3-s-simple-investing.asp',
    ),
    EduItem(
      title: 'How and Where to Start Investing',
      url: 'https://www.investopedia.com/terms/i/investment.asp',
    ),
  ],
  'retirement': [
    EduItem(
      title: 'How to Invest for Retirement',
      url:
          'https://www.khanacademy.org/college-careers-more/financial-literacy/xa6995ea67a8e9fdd:investments-retirement/xa6995ea67a8e9fdd:investing/a/how-to-invest-in-your-retirement-account',
    ),
    EduItem(
      title: 'Building a Strong Foundation for Retirement',
      url:
          'https://www.khanacademy.org/college-careers-more/personal-finance/pf-investment-vehicles-insurance-and-retirement/pf-ira-401ks/a/building-a-foundation-for-retirement',
    ),
    EduItem(
      title: 'The Effect of Time on Your Retirement Savings',
      url:
          'https://www.khanacademy.org/college-careers-more/personal-finance/pf-investment-vehicles-insurance-and-retirement/pf-ira-401ks/a/the-effect-of-time-on-your-retirement-account',
    ),
    EduItem(
      title: 'Pensions, 403(b)s, and SIMPLE IRAs Explained',
      url:
          'https://www.khanacademy.org/college-careers-more/financial-literacy/xa6995ea67a8e9fdd:investments-retirement/xa6995ea67a8e9fdd:saving-for-retirement/a/what-is-a-pension-403-b-simple-ira-and-others',
    ),
  ],
  'budgeting': [
    EduItem(
      title: 'What Is a Budget?',
      url:
          'https://www.khanacademy.org/college-careers-more/financial-literacy/xa6995ea67a8e9fdd:budgeting-and-saving/xa6995ea67a8e9fdd:budgeting/a/what-is-a-budget',
    ),
    EduItem(
      title: 'A Step-by-Step Guide to Creating a Budget',
      url:
          'https://www.khanacademy.org/college-careers-more/personal-finance/pf-saving-and-budgeting/tips-for-tracking-and-saving-money/a/creating-a-budget',
    ),
    EduItem(
      title: 'How to Balance Your Budget',
      url:
          'https://www.khanacademy.org/college-careers-more/financial-literacy/xa6995ea67a8e9fdd:budgeting-and-saving/xa6995ea67a8e9fdd:budgeting/a/balancing-your-budget',
    ),
    EduItem(
      title: 'Understanding Budgeting Constraints and Decisions',
      url:
          'https://www.khanacademy.org/economics-finance-domain/microeconomics/choices-opp-cost-tutorial/utility-maximization-with-indifference-curves/a/how-individuals-make-choices-based-on-their-budget-constraint-cnx',
    ),
  ],
  'taxes': [
    EduItem(
      title: 'An Overview of Common Tax Forms',
      url:
          'https://www.khanacademy.org/college-careers-more/financial-literacy/xa6995ea67a8e9fdd:taxes-and-tax-forms/xa6995ea67a8e9fdd:tax-forms/a/tax-forms',
    ),
    EduItem(
      title: 'Your Guide to Key Tax Terms',
      url:
          'https://www.khanacademy.org/math/grade-7-math-tx/xa876d090ec748f45:number-and-operations/xa876d090ec748f45:income-tax-withholding/a/your-guide-to-key-tax-terms-brought-to-you-by-better-money-habits',
    ),
    EduItem(
      title: 'Understanding the Taxes You Pay',
      url:
          'https://www.khanacademy.org/college-careers-more/financial-literacy/xa6995ea67a8e9fdd:taxes-and-tax-forms/xa6995ea67a8e9fdd:what-are-taxes/a/understanding-the-taxes-you-pay',
    ),
    EduItem(
      title: 'A Guide to Taxes for the Self-Employed',
      url:
          'https://www.khanacademy.org/college-careers-more/financial-literacy/xa6995ea67a8e9fdd:employment/xa6995ea67a8e9fdd:non-typical-pay-structures/a/tax-responsibilities-for-self-employed-individuals',
    ),
  ],
};

/// Type of an indexed/search document.
enum EduDocType { video, article }

/// A flattened, searchable record describing one [EduItem] in context.
class EduSearchDoc {
  const EduSearchDoc({
    required this.topic,
    required this.topicName,
    required this.type,
    required this.title,
    required this.url,
    required this.index,
  });

  final String topic;
  final String topicName;
  final EduDocType type;
  final String title;
  final String url;
  final int index;
}

String _topicName(String id) {
  for (final t in eduTopics) {
    if (t.id == id) return t.name;
  }
  return id;
}

/// Flattened index of every video + article, mirroring `EDU_SEARCH_INDEX`.
final List<EduSearchDoc> eduSearchIndex = [
  for (final entry in videoLessons.entries)
    for (var index = 0; index < entry.value.length; index++)
      EduSearchDoc(
        topic: entry.key,
        topicName: _topicName(entry.key),
        type: EduDocType.video,
        title: entry.value[index].title,
        url: entry.value[index].url,
        index: index,
      ),
  for (final entry in readingResources.entries)
    for (var index = 0; index < entry.value.length; index++)
      EduSearchDoc(
        topic: entry.key,
        topicName: _topicName(entry.key),
        type: EduDocType.article,
        title: entry.value[index].title,
        url: entry.value[index].url,
        index: index,
      ),
];

// --- Relevance search --------------------------------------------------------

const Set<String> _stop = {
  'how', 'to', 'what', 'is', 'are', 'a', 'an', 'the', 'and', 'or', 'of', 'in',
  'for', 'my', 'do', 'i', 'on', 'with', 'you', 'your', 'vs', 'me', 'can',
  'should', 'about', 'best', 'way', 'ways', 'tips', 'guide', 'explain',
  'explained',
};

final RegExp _nonAlnum = RegExp(r'[^a-z0-9]+');
final RegExp _stemSuffix = RegExp(r'(ings|ing|ies|ied|ed|es|s)$');

List<String> _tokenize(String s) {
  return s
      .toLowerCase()
      .split(_nonAlnum)
      .where((t) => t.length > 1 && !_stop.contains(t))
      .toList();
}

String _stem(String t) {
  final stemmed = t.replaceFirst(_stemSuffix, '');
  return stemmed.isEmpty ? t : stemmed;
}

/// Ranks education content against a free-text query. Forgiving: matches on
/// stems, prefixes, and substrings so "how to invest" surfaces "A Beginner's
/// Guide to Investing", "How and Where to Start Investing", etc. Returns []
/// when nothing is reasonably related.
List<EduSearchDoc> searchEducation(String query) {
  final q = query.trim().toLowerCase();
  if (q.length < 2) return [];

  final qTokens = _tokenize(q).map(_stem).toList();
  if (qTokens.isEmpty) {
    // Query was all stopwords — fall back to a plain substring match.
    return eduSearchIndex
        .where((d) =>
            '${d.title} ${d.topicName}'.toLowerCase().contains(q))
        .toList();
  }

  final scored = <_Scored>[];
  for (final doc in eduSearchIndex) {
    final titleText = doc.title.toLowerCase();
    final fullText = '${doc.title} ${doc.topicName}'.toLowerCase();
    final docTokens = _tokenize(fullText).map(_stem).toList();
    var score = 0.0;

    if (titleText.contains(q)) {
      score += 12; // whole phrase in the title
    } else if (fullText.contains(q)) {
      score += 8;
    }

    var matched = 0;
    for (final qt in qTokens) {
      if (docTokens.contains(qt)) {
        score += 5;
        matched++;
      } else if (docTokens.any((dt) => dt.startsWith(qt) || qt.startsWith(dt))) {
        score += 3;
        matched++;
      } else if (fullText.contains(qt)) {
        score += 1.5;
        matched++;
      }
    }
    // Reward covering more of the query.
    if (matched == qTokens.length && qTokens.length > 1) score += 3;

    scored.add(_Scored(doc, score));
  }

  final results = scored.where((x) => x.score > 0).toList()
    ..sort((a, b) => b.score.compareTo(a.score));

  return results.take(24).map((x) => x.doc).toList();
}

class _Scored {
  const _Scored(this.doc, this.score);

  final EduSearchDoc doc;
  final double score;
}
