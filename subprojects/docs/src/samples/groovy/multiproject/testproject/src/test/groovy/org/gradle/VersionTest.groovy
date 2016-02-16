package org.gradle

import org.junit.Test

import static org.junit.Assert.assertEquals

class GroovycVersionTest {
  def groovycVersion

  @Test
  void versionShouldBe2_4_5() {
    assertEquals("2.4.5", groovycVersion)
  }
}
