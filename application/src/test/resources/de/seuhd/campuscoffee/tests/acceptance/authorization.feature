Feature: Role-based authorization for write requests

  Read access is public; every write request needs an authenticated user with the required role. Curating a
  point of sale requires MODERATOR; the ADMIN role alone does not grant it.

  Scenario: An unauthenticated user cannot create a point of sale
    When an unauthenticated user creates a point of sale
    Then the write request is unauthorized

  Scenario: A plain user cannot create a point of sale
    When a plain user creates a point of sale
    Then the write request is forbidden

  Scenario: An admin without moderator rights cannot create a point of sale
    When an admin without moderation creates a point of sale
    Then the write request is forbidden

  Scenario: A moderator creates a point of sale
    When a moderator creates a point of sale
    Then the write request succeeds

  Scenario: A moderator may edit a review they did not author
    Given a review authored by a plain user
    When a moderator edits that review
    Then the write request succeeds

  Scenario: An admin without moderator rights cannot edit a review they did not author
    Given a review authored by a plain user
    When an admin without moderation edits that review
    Then the write request is forbidden

  Scenario: A review's author cannot approve their own review
    Given a review authored by a plain user
    When a plain user approves that review
    Then the write request is a bad request

  Scenario: A user cannot approve the same review twice
    Given a review authored by a plain user
    When a moderator approves that review
    Then the write request succeeds
    When a moderator approves that review again
    Then the write request is a conflict
