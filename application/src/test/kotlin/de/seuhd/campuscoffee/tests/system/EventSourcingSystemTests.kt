package de.seuhd.campuscoffee.tests.system

import org.springframework.test.context.TestPropertySource

/**
 * System tests are persistence-agnostic, so the same suites must pass on either backend. These subclasses
 * re-run the existing system-test suites unchanged against the event sourcing backend: the added property
 * forks a separate Spring context in event sourcing mode (Spring keys the context cache on the merged
 * properties), so every inherited test drives the event-sourced decorators over HTTP. No test logic is
 * duplicated; only the persistence mode differs.
 */
@TestPropertySource(properties = ["campus-coffee.persistence.mode=event-sourcing"])
class EventSourcingPosSystemTests : PosSystemTests()

@TestPropertySource(properties = ["campus-coffee.persistence.mode=event-sourcing"])
class EventSourcingReviewSystemTests : ReviewSystemTests()

@TestPropertySource(properties = ["campus-coffee.persistence.mode=event-sourcing"])
class EventSourcingUserSystemTests : UsersSystemTests()
