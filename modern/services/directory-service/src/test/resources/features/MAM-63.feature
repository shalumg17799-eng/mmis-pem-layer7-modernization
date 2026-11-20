@MAM-63
Feature: Public provider directory search
  GET /api/v1/directory/providers answers anyone, without signing in, from the directory's own data (PRVDIR01 replaced,
  D-013, Q-008): ACTIVE providers only, filters by name (contains, any case), provider type and taxonomy, paged in NPI
  order, and only the published facts.

  Background:
    Given the directory holds:
      | npi        | medicaidId | type | tier | status | name               | taxonomy   |
      | 1003000111 | M00000111  | IN   | L    | A      | MARIA RIVERA       | 207Q00000X |
      | 1003000333 | M00000333  | FA   | H    | A      | SUNSET HOSPICE INC | 251G00000X |
      | 1003000444 | M00000444  | GR   |      | D      | EXCLUDED CLINIC    | 251E00000X |
      | 1003000555 | M00000555  | IN   |      | P      | PAT SAMPLE         | 225100000X |
      | 1003000666 | M00000666  | FA   | H    | S      | SAMPLE HOSPICE 09  | 251G00000X |

  @AC-1 @AC-2
  Scenario: Anyone can search without signing in, and only ACTIVE providers are listed
    When anyone searches the directory with ""
    Then the search answers 200 with the NPIs "1003000111,1003000333", page 0, size 20 and total 2

  @AC-3
  Scenario: Search by name finds the composed name, in any case
    When anyone searches the directory with "name=HOSPICE"
    Then the search answers 200 with the NPIs "1003000333", page 0, size 20 and total 1
    When anyone searches the directory with "name=rivera"
    Then the search answers 200 with the NPIs "1003000111", page 0, size 20 and total 1
    When anyone searches the directory with "name=ZZZZ"
    Then the search answers 200 with the NPIs "", page 0, size 20 and total 0

  @AC-3
  Scenario: A percent sign or an underscore in the searched name is a literal character, not a wildcard
    When anyone searches the directory with "name=%"
    Then the search answers 200 with the NPIs "", page 0, size 20 and total 0
    When anyone searches the directory with "name=_"
    Then the search answers 200 with the NPIs "", page 0, size 20 and total 0

  @AC-3
  Scenario: A name that holds a literal percent sign or underscore is found by that character only
    Given the directory holds:
      | npi        | medicaidId | type | tier | status | name              | taxonomy   |
      | 1003000111 | M00000111  | IN   | L    | A      | MARIA RIVERA      | 207Q00000X |
      | 1003000777 | M00000777  | GR   |      | A      | PLAN 100% CARE    | 251E00000X |
      | 1003000888 | M00000888  | GR   |      | A      | NORTH_SIDE CLINIC | 251E00000X |
    When anyone searches the directory with "name=%"
    Then the search answers 200 with the NPIs "1003000777", page 0, size 20 and total 1
    When anyone searches the directory with "name=_"
    Then the search answers 200 with the NPIs "1003000888", page 0, size 20 and total 1

  @AC-4
  Scenario: Filter by provider type
    When anyone searches the directory with "providerType=IN"
    Then the search answers 200 with the NPIs "1003000111", page 0, size 20 and total 1
    When anyone searches the directory with "providerType=FA"
    Then the search answers 200 with the NPIs "1003000333", page 0, size 20 and total 1
    When anyone searches the directory with "providerType=XX"
    Then the search answers 400 problem+json

  @AC-5
  Scenario: Filter by taxonomy and combine filters
    When anyone searches the directory with "taxonomy=207Q00000X"
    Then the search answers 200 with the NPIs "1003000111", page 0, size 20 and total 1
    When anyone searches the directory with "taxonomy=207Q00000X&providerType=FA"
    Then the search answers 200 with the NPIs "", page 0, size 20 and total 0

  @AC-6
  Scenario: Results are paged in a stable order
    Given the directory holds 25 ACTIVE entries with NPI 1003001001 to 1003001025 and no others
    When anyone searches the directory with "page=0&size=10"
    Then the search answers 200 with the NPIs 1003001001 to 1003001010, page 0, size 10 and total 25
    When anyone searches the directory with "page=2&size=10"
    Then the search answers 200 with the NPIs 1003001021 to 1003001025, page 2, size 10 and total 25
    When anyone searches the directory with "size=201"
    Then the search answers 400 problem+json
    When anyone searches the directory with "size=0"
    Then the search answers 400 problem+json
    When anyone searches the directory with "page=-1"
    Then the search answers 400 problem+json

  @AC-7
  Scenario: Only the published facts are returned
    When anyone searches the directory with "name=RIVERA"
    Then every item has exactly the fields npi, medicaidId, providerType, riskTier, displayName and taxonomy
