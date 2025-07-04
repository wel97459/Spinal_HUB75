package hub75

import spinal.core._
import spinal.core.sim._

object Config {
  def spinal = SpinalConfig(
    targetDirectory = "hw/gen",
    device = Device.LATTICE,
    defaultConfigForClockDomains = ClockDomainConfig(
      resetKind = SYNC
    )
  )

  def sim = SimConfig.withConfig(spinal).withFstWave
}
