Feature: Reviews and the approval workflow

  The approval quorum is three: a review becomes approved once three users other than the author
  have approved it.

  Background:
    Given the following users exist:
      | loginName | emailAddress                | firstName | lastName |
      | author    | author@uni-heidelberg.de    | Aria      | Author   |
      | reviewer1 | reviewer1@uni-heidelberg.de | Rey       | One      |
      | reviewer2 | reviewer2@uni-heidelberg.de | Rey       | Two      |
      | reviewer3 | reviewer3@uni-heidelberg.de | Rey       | Three    |
    And a POS named "Schmelzpunkt" exists

  Scenario: A newly created review is not yet approved
    When "author" reviews "Schmelzpunkt" with "Great waffles and quick service."
    Then the review by "author" for "Schmelzpunkt" is not approved

  Scenario: A review becomes approved when it reaches the quorum
    Given "author" reviewed "Schmelzpunkt" with "Great waffles and quick service."
    When "reviewer1" approves the review by "author" for "Schmelzpunkt"
    And "reviewer2" approves the review by "author" for "Schmelzpunkt"
    And "reviewer3" approves the review by "author" for "Schmelzpunkt"
    Then the review by "author" for "Schmelzpunkt" is approved

  Scenario: A review stays unapproved below the quorum
    Given "author" reviewed "Schmelzpunkt" with "Great waffles and quick service."
    When "reviewer1" approves the review by "author" for "Schmelzpunkt"
    And "reviewer2" approves the review by "author" for "Schmelzpunkt"
    Then the review by "author" for "Schmelzpunkt" is not approved

  Scenario: An author cannot approve their own review
    Given "author" reviewed "Schmelzpunkt" with "Great waffles and quick service."
    When "author" tries to approve the review by "author" for "Schmelzpunkt"
    Then the approval is rejected
