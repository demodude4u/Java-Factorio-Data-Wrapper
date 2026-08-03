package com.demod.factorio;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.demod.factorio.ModInfo.DepOp;
import com.demod.factorio.ModInfo.DepPrefix;
import com.demod.factorio.ModInfo.Dependency;

public class ModInfoDependencyTest {

	@Test
	public void parsesRequiredDependencyWithoutPrefix() {
		Dependency dependency = Dependency.parse("flib >= 0.16.2");

		assertEquals("flib", dependency.getName());
		assertEquals(DepPrefix.REQUIRED, dependency.getPrefix());
		assertEquals(DepOp.GTE, dependency.getOp());
		assertEquals("0.16.2", dependency.getVersion());
		assertTrue(dependency.isRequired());
	}

	@Test
	public void parsesLoadAfterDependencyWithPlusPrefix() {
		Dependency dependency = Dependency.parse("+ ChangeInserterDropLane >= 1.3.0");

		assertEquals("ChangeInserterDropLane", dependency.getName());
		assertEquals(DepPrefix.REQUIRED_LOAD_AFTER, dependency.getPrefix());
		assertEquals(DepOp.GTE, dependency.getOp());
		assertEquals("1.3.0", dependency.getVersion());
		assertTrue(dependency.isRequired());
		assertFalse(dependency.isOptional());
		assertFalse(dependency.isIncompatible());
	}

	@Test
	public void parsesIncompatibleDependency() {
		Dependency dependency = Dependency.parse("! Annotorio");

		assertEquals("Annotorio", dependency.getName());
		assertEquals(DepPrefix.INCOMPATIBLE, dependency.getPrefix());
		assertNull(dependency.getVersion());
		assertTrue(dependency.isIncompatible());
	}

	@Test
	public void parsesHiddenOptionalDependency() {
		Dependency dependency = Dependency.parse("(?) space-age");

		assertEquals("space-age", dependency.getName());
		assertEquals(DepPrefix.HIDDEN_OPTIONAL, dependency.getPrefix());
		assertTrue(dependency.isOptional());
	}

	@Test
	public void parsesOptionalDependencyWithVersion() {
		Dependency dependency = Dependency.parse("? Aircraft >= 1.6.6");

		assertEquals("Aircraft", dependency.getName());
		assertEquals(DepPrefix.OPTIONAL, dependency.getPrefix());
		assertEquals(DepOp.GTE, dependency.getOp());
		assertEquals("1.6.6", dependency.getVersion());
	}

	@Test
	public void parsesLoadOrderIndependentDependency() {
		Dependency dependency = Dependency.parse("~ base >= 2.0.0");

		assertEquals("base", dependency.getName());
		assertEquals(DepPrefix.DOES_NOT_AFFECT_LOAD_ORDER, dependency.getPrefix());
		assertTrue(dependency.isRequired());
	}
}
