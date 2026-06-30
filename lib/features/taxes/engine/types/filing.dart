// Filing status, taxpayer identity, residency, and dependents.
// Maps to Form 1040 page 1 (filing status, dependents) and Schedule 8812.

/// Form 1040 filing status checkboxes.
///  - single: Single
///  - mfj: Married filing jointly
///  - mfs: Married filing separately
///  - hoh: Head of household
///  - qss: Qualifying surviving spouse
enum FilingStatus { single, mfj, mfs, hoh, qss }

/// Two-letter USPS state codes (+ DC). Modeled as a plain [String] alias.
/// Residency drives the state engine (later phase).
typedef StateCode = String;

/// Individual identity block (taxpayer or spouse). SSN is sensitive — never
/// persisted.
class TaxpayerInfo {
  TaxpayerInfo({
    this.firstName = '',
    this.lastName = '',
    this.ssn = '',
    this.dateOfBirth = '',
    this.occupation = '',
    this.blind = false,
    this.claimedAsDependentByAnother = false,
  });

  String firstName;
  String lastName;

  /// SSN — SENSITIVE: held in memory only, never written to localStorage.
  String ssn;

  /// ISO date string (YYYY-MM-DD). Used to derive the 65+ additional standard
  /// deduction.
  String dateOfBirth;
  String occupation;

  /// Legally blind — adds an additional standard deduction amount
  /// (Form 1040 std-ded chart).
  bool blind;

  /// Can be claimed as a dependent on someone else's return — caps the
  /// standard deduction.
  bool claimedAsDependentByAnother;
}

/// Mailing address (Form 1040 header).
class Address {
  Address({
    this.line1 = '',
    this.line2,
    this.city = '',
    this.state = '',
    this.zip = '',
  });

  String line1;
  String? line2;
  String city;

  /// [StateCode] or empty string.
  String state;
  String zip;
}

/// A dependent claimed on the return.
///
/// Qualification follows the IRS qualifying-child / qualifying-relative tests
/// (Pub 17 ch. 3). The booleans below are the engine inputs that gate CTC/ODC,
/// the Child & Dependent Care Credit, and EITC.
class Dependent {
  Dependent({
    required this.id,
    this.firstName = '',
    this.lastName = '',
    this.ssn = '',
    this.dateOfBirth = '',
    required this.relationshipType,
    this.relationship = '',
    this.monthsLivedWithTaxpayer = 0,
    this.taxpayerProvidedOverHalfSupport = false,
    this.qualifiesForCTC = false,
    this.qualifiesForODC = false,
    this.qualifiesForEITC = false,
    this.qualifiesForCareCredit = false,
  });

  String id;
  String firstName;
  String lastName;

  /// SENSITIVE.
  String ssn;
  String dateOfBirth;

  /// "child" = qualifying child; "relative" = qualifying relative.
  String relationshipType;

  /// e.g. "son", "daughter", "parent".
  String relationship;

  /// Months the dependent lived with the taxpayer in 2024 (residency test).
  double monthsLivedWithTaxpayer;

  /// Taxpayer provided > half of the dependent's support.
  bool taxpayerProvidedOverHalfSupport;

  /// Qualifies for the \$2,000 Child Tax Credit (under 17 at year end, has SSN,
  /// etc.).
  bool qualifiesForCTC;

  /// Qualifies for the \$500 Credit for Other Dependents (ODC) instead of CTC.
  bool qualifiesForODC;

  /// Counts as a qualifying child for EITC purposes
  /// (relationship/age/residency).
  bool qualifiesForEITC;

  /// Under 13 (or disabled) — gates the Child & Dependent Care Credit
  /// (Form 2441).
  bool qualifiesForCareCredit;
}
