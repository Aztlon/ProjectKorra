package com.projectkorra.projectkorra.event;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class StandardAbilityExecutionEvidenceWiringTest {
	private static final Path PROJECT_ROOT = locateProjectRoot();

	@Test
	void damageEvidenceIsAfterObservedHealthAndAbsorptionChanges() throws Exception {
		String source = Files.readString(PROJECT_ROOT.resolve(
				"src/com/projectkorra/projectkorra/util/DamageHandler.java"));
		int damage = source.indexOf("lent.damage(damage");
		int nextHealth = source.indexOf("final double nextHealth", damage);
		int publication = source.indexOf("AbilityExecutionEvidence.publishEntity", nextHealth);

		assertTrue(damage >= 0);
		assertTrue(nextHealth > damage);
		assertTrue(publication > nextHealth);
		assertTrue(source.contains("prevAbsorption"));
		assertTrue(source.contains("nextAbsorption"));
		assertTrue(source.contains("appliedDamage > 0D"));
	}

	@Test
	void velocityEvidenceIsAfterFinalVelocityAssignment() throws Exception {
		String source = Files.readString(PROJECT_ROOT.resolve(
				"src/com/projectkorra/projectkorra/GeneralMethods.java"));
		int assignment = source.indexOf("event.getAffected().setVelocity(velocity)");
		int publication = source.indexOf("AbilityExecutionEvidence.publishEntity", assignment);

		assertTrue(assignment >= 0);
		assertTrue(publication > assignment);
		assertTrue(source.contains("sourceAbility != null"));
		assertTrue(source.contains("appliedMagnitude > 0D"));
	}

	@Test
	void semanticTrainingEvidenceFollowsEachCommittedEffect() throws Exception {
		String airShield = Files.readString(PROJECT_ROOT.resolve(
				"src/com/projectkorra/projectkorra/airbending/AirShield.java"));
		String blaze = Files.readString(PROJECT_ROOT.resolve(
				"src/com/projectkorra/projectkorra/firebending/BlazeArc.java"));
		String waterBubble = Files.readString(PROJECT_ROOT.resolve(
				"src/com/projectkorra/projectkorra/waterbending/WaterBubble.java"));

		assertTrue(airShield.indexOf("AbilityExecutionEvidence.publishEntity(this")
				> airShield.indexOf("GeneralMethods.trySetVelocity(this, entity, velocity)"));
		assertTrue(airShield.contains("&& velocityApplied"));
		assertTrue(airShield.contains("entity instanceof LivingEntity || entity instanceof Projectile"));
		assertTrue(blaze.indexOf("AbilityExecutionEvidence.publishBlock(this")
				> blaze.indexOf("createTempFire(block.getLocation()"));
		assertTrue(waterBubble.indexOf("AbilityExecutionEvidence.publishLocation(this")
				> waterBubble.indexOf("b.setType(Material.AIR)"));
		assertTrue(waterBubble.contains("executionEvidencePublished"));
	}

	private static Path locateProjectRoot() {
		Path directory = Path.of("").toAbsolutePath();
		while (directory != null) {
			if (Files.isRegularFile(directory.resolve("pom.xml"))
					&& Files.isDirectory(directory.resolve("src/com/projectkorra/projectkorra"))) {
				return directory;
			}
			directory = directory.getParent();
		}
		throw new IllegalStateException("Could not locate the ProjectKorra source root from the working directory.");
	}
}
