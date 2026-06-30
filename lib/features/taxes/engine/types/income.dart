/// Income source types. Every field maps to a specific information return box
/// or Schedule line. The engine reads these only when the corresponding income
/// flag is set, so hidden/irrelevant sources never affect the calculation.
library;

/// A single Box 12 code/amount entry on a W-2.
class Box12Entry {
  Box12Entry({required this.code, required this.amount});

  String code;
  double amount;
}

/// A W-2 (wage statement). Box numbers per the 2024 W-2.
class W2 {
  W2({
    required this.id,
    required this.owner,
    this.employerName = '',
    this.box1Wages = 0,
    this.box2FederalWithholding = 0,
    this.box3SsWages = 0,
    this.box4SsWithheld = 0,
    this.box5MedicareWages = 0,
    this.box6MedicareWithheld = 0,
    List<Box12Entry>? box12,
    this.statutoryEmployee = false,
    this.box17StateWithholding = 0,
  }) : box12 = box12 ?? [];

  String id;

  /// "taxpayer" or "spouse" — relevant for MFJ Additional Medicare Tax
  /// per-person wage base.
  String owner;
  String employerName;

  /// Box 1 — wages, tips, other compensation.
  double box1Wages;

  /// Box 2 — federal income tax withheld.
  double box2FederalWithholding;

  /// Box 3 — Social Security wages.
  double box3SsWages;

  /// Box 4 — Social Security tax withheld.
  double box4SsWithheld;

  /// Box 5 — Medicare wages and tips (basis for Additional Medicare Tax).
  double box5MedicareWages;

  /// Box 6 — Medicare tax withheld (includes any Additional Medicare withheld).
  double box6MedicareWithheld;

  /// Box 12 codes that matter to the engine (D=401k, W=HSA employer/employee,
  /// etc.).
  List<Box12Entry> box12;

  /// Box 13 "Statutory employee" — moves wages to Schedule C.
  bool statutoryEmployee;

  /// Box 17 — state income tax withheld (state engine, later phase).
  double box17StateWithholding;
}

/// 1099-INT — interest income.
class Form1099Int {
  Form1099Int({
    required this.id,
    this.payer = '',
    this.box1Interest = 0,
    this.box3UsTreasuryInterest = 0,
    this.box8TaxExemptInterest = 0,
    this.box4FederalWithholding = 0,
  });

  String id;
  String payer;

  /// Box 1 — taxable interest.
  double box1Interest;

  /// Box 3 — interest on U.S. savings bonds / Treasury obligations
  /// (state-exempt).
  double box3UsTreasuryInterest;

  /// Box 8 — tax-exempt interest (feeds SS taxability & some MAGIs, not taxable
  /// income).
  double box8TaxExemptInterest;

  /// Box 4 — federal income tax withheld (backup withholding).
  double box4FederalWithholding;
}

/// 1099-DIV — dividends. Qualified portion is taxed at capital-gains rates.
class Form1099Div {
  Form1099Div({
    required this.id,
    this.payer = '',
    this.box1aOrdinaryDividends = 0,
    this.box1bQualifiedDividends = 0,
    this.box2aCapitalGainDistributions = 0,
    this.box4FederalWithholding = 0,
  });

  String id;
  String payer;

  /// Box 1a — total ordinary dividends.
  double box1aOrdinaryDividends;

  /// Box 1b — qualified dividends (subset of 1a, capital-gain rates).
  double box1bQualifiedDividends;

  /// Box 2a — total capital gain distributions (long-term).
  double box2aCapitalGainDistributions;

  /// Box 4 — federal income tax withheld.
  double box4FederalWithholding;
}

/// One sale lot from a 1099-B / Form 8949 capital transaction.
class CapitalTransaction {
  CapitalTransaction({
    required this.id,
    this.description = '',
    this.proceeds = 0,
    this.costBasis = 0,
    this.longTerm = false,
    this.washSaleAdjustment,
  });

  String id;
  String description;
  double proceeds;
  double costBasis;

  /// true = held > 1 year (long-term); false = short-term (ordinary rates).
  bool longTerm;

  /// Wash-sale disallowed loss adjustment (8949 col g), if any.
  double? washSaleAdjustment;
}

/// 1099-R — distributions from pensions, annuities, retirement, IRAs.
class Form1099R {
  Form1099R({
    required this.id,
    this.payer = '',
    this.box1GrossDistribution = 0,
    this.box2aTaxableAmount = 0,
    this.box4FederalWithholding = 0,
    this.box7DistributionCode = '',
    this.iraSepSimple = false,
  });

  String id;
  String payer;

  /// Box 1 — gross distribution.
  double box1GrossDistribution;

  /// Box 2a — taxable amount.
  double box2aTaxableAmount;

  /// Box 4 — federal income tax withheld.
  double box4FederalWithholding;

  /// Box 7 — distribution code (e.g. "1" = early, no known exception → 10%
  /// penalty).
  String box7DistributionCode;

  /// IRA/SEP/SIMPLE checkbox.
  bool iraSepSimple;
}

/// 1099-SSA — Social Security benefits (taxability computed via worksheet).
class Form1099Ssa {
  Form1099Ssa({
    required this.id,
    required this.owner,
    this.box5NetBenefits = 0,
    this.federalWithholding = 0,
  });

  String id;
  String owner;

  /// Box 5 — net benefits for the year.
  double box5NetBenefits;

  /// Federal income tax withheld (voluntary).
  double federalWithholding;
}

/// 1099-NEC — nonemployee compensation (flows to Schedule C).
class Form1099Nec {
  Form1099Nec({
    required this.id,
    this.payer = '',
    this.box1Compensation = 0,
    this.box4FederalWithholding = 0,
  });

  String id;
  String payer;

  /// Box 1 — nonemployee compensation.
  double box1Compensation;
  double box4FederalWithholding;
}

/// 1099-MISC — miscellaneous income (rents, royalties, other).
class Form1099Misc {
  Form1099Misc({
    required this.id,
    this.payer = '',
    this.box1Rents = 0,
    this.box2Royalties = 0,
    this.box3OtherIncome = 0,
    this.box4FederalWithholding = 0,
  });

  String id;
  String payer;
  double box1Rents;
  double box2Royalties;
  double box3OtherIncome;
  double box4FederalWithholding;
}

/// 1099-G — government payments (unemployment, state refunds).
class Form1099G {
  Form1099G({
    required this.id,
    this.payer = '',
    this.box1Unemployment = 0,
    this.box2StateRefund = 0,
    this.box4FederalWithholding = 0,
  });

  String id;
  String payer;

  /// Box 1 — unemployment compensation (fully taxable for 2024).
  double box1Unemployment;

  /// Box 2 — state/local income tax refunds (taxable only if itemized last
  /// year).
  double box2StateRefund;
  double box4FederalWithholding;
}

/// 1099-SA — distributions from an HSA (Form 8889).
class Form1099Sa {
  Form1099Sa({
    required this.id,
    this.box1GrossDistribution = 0,
    this.unqualifiedAmount = 0,
  });

  String id;

  /// Box 1 — gross distribution.
  double box1GrossDistribution;

  /// Portion used for unqualified expenses (taxable + 20% penalty).
  double unqualifiedAmount;
}

/// A single Schedule C business.
class ScheduleC {
  ScheduleC({
    required this.id,
    required this.owner,
    this.businessName = '',
    this.description = '',
    this.grossReceipts = 0,
    this.costOfGoodsSold = 0,
    Map<String, double>? expenses,
    this.homeOfficeDeduction = 0,
    this.vehicleExpense = 0,
    this.isSSTB = false,
  }) : expenses = expenses ?? {};

  String id;
  String owner;
  String businessName;

  /// Principal business / activity description.
  String description;

  /// Line 1 — gross receipts.
  double grossReceipts;

  /// Line 4 — cost of goods sold.
  double costOfGoodsSold;

  /// Itemized expense lines (Part II), keyed by category.
  Map<String, double> expenses;

  /// Home office deduction (Form 8829 or simplified \$5/sqft up to 300 sqft).
  double homeOfficeDeduction;

  /// Vehicle expenses (actual or standard mileage).
  double vehicleExpense;

  /// Whether the activity is a specified service trade or business (QBI/SSTB).
  bool isSSTB;
}

/// A single Schedule E property / passthrough (rental, royalty, K-1).
class ScheduleE {
  ScheduleE({
    required this.id,
    this.description = '',
    this.netIncome = 0,
  });

  String id;
  String description;

  /// Net rental/royalty/passthrough income or loss for the property.
  double netIncome;
}

/// Gating flags driven by the interview; the engine only reads gated sources.
class IncomeFlags {
  IncomeFlags({
    this.hasW2 = false,
    this.hasInterest = false,
    this.hasDividends = false,
    this.hasCapitalGains = false,
    this.hasRetirementDistributions = false,
    this.hasSocialSecurity = false,
    this.hasSelfEmployment = false,
    this.hasRental = false,
    this.hasUnemployment = false,
    this.hasOtherIncome = false,
  });

  bool hasW2;
  bool hasInterest;
  bool hasDividends;
  bool hasCapitalGains;
  bool hasRetirementDistributions;
  bool hasSocialSecurity;
  bool hasSelfEmployment;
  bool hasRental;
  bool hasUnemployment;
  bool hasOtherIncome;
}

/// Container for all income on the return.
class IncomeData {
  IncomeData({
    List<W2>? w2,
    List<Form1099Int>? f1099Int,
    List<Form1099Div>? f1099Div,
    List<CapitalTransaction>? f1099B,
    List<Form1099R>? f1099R,
    List<Form1099Ssa>? f1099Ssa,
    List<Form1099Nec>? f1099Nec,
    List<Form1099Misc>? f1099Misc,
    List<Form1099G>? f1099G,
    List<Form1099Sa>? f1099Sa,
    List<ScheduleC>? scheduleC,
    List<ScheduleE>? scheduleE,
    this.otherIncome = 0,
    this.capitalLossCarryoverShort = 0,
    this.capitalLossCarryoverLong = 0,
    IncomeFlags? flags,
  })  : w2 = w2 ?? [],
        f1099Int = f1099Int ?? [],
        f1099Div = f1099Div ?? [],
        f1099B = f1099B ?? [],
        f1099R = f1099R ?? [],
        f1099Ssa = f1099Ssa ?? [],
        f1099Nec = f1099Nec ?? [],
        f1099Misc = f1099Misc ?? [],
        f1099G = f1099G ?? [],
        f1099Sa = f1099Sa ?? [],
        scheduleC = scheduleC ?? [],
        scheduleE = scheduleE ?? [],
        flags = flags ?? IncomeFlags();

  List<W2> w2;
  List<Form1099Int> f1099Int;
  List<Form1099Div> f1099Div;
  List<CapitalTransaction> f1099B;
  List<Form1099R> f1099R;
  List<Form1099Ssa> f1099Ssa;
  List<Form1099Nec> f1099Nec;
  List<Form1099Misc> f1099Misc;
  List<Form1099G> f1099G;
  List<Form1099Sa> f1099Sa;
  List<ScheduleC> scheduleC;
  List<ScheduleE> scheduleE;

  /// Catch-all other income (Schedule 1 line 8z).
  double otherIncome;

  /// Prior-year capital loss carryover into 2024 (Schedule D).
  double capitalLossCarryoverShort;
  double capitalLossCarryoverLong;

  /// Gating flags driven by the interview; the engine only reads gated sources.
  IncomeFlags flags;
}
