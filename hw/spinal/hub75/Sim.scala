//Generate the MyTopLevel's Verilog using the above custom configuration.
package hub75

import spinal.core._
import spinal.lib._
import spinal.core.sim._

object SimConfig extends SpinalConfig(
    targetDirectory = "hw/gen",
    oneFilePerComponent = false,
    defaultConfigForClockDomains = ClockDomainConfig(resetKind = SYNC)
)