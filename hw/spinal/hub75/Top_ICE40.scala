package hub75

import spinal.core._
import spinal.lib._
import spinal.lib.blackbox.lattice.ice40._

import MySpinalHardware._


class SPRAM() extends BlackBox {
    val io = new Bundle {
        val DATAIN = in Bits(16 bits)
        val ADDRESS = in Bits(14 bits)
        val MASKWREN = in Bits(4 bits)
        val WREN = in Bool()
        val CHIPSELECT = in Bool()
        val CLOCK = in Bool()
        val STANDBY = in Bool()
        val SLEEP = in Bool()
        val POWEROFF = in Bool()
        val DATAOUT = out Bits(16 bits)
    }
    noIoPrefix()
    setBlackBoxName("SB_SPRAM256KA")
    // Map the current clock domain to the io.clk pin
    mapClockDomain(clock=io.CLOCK)
}

//Hardware definition
class Top_ICE40() extends Component {
    val io = new Bundle {
        val reset_ = in Bool()
        val clk_12Mhz = in Bool() //12Mhz CLK

        //val serial_txd = out Bool()
        val serial_rxd = in Bool()

        // val led_red = out Bool()
        // val led_green = out Bool()
        // val led_blue = out Bool()
        val hub75 = new Bundle {
            val RGB0 = new Bundle {
                val R = out Bool()
                val G = out Bool()
                val B = out Bool()
            }
            val RGB1 = new Bundle {
                val R = out Bool()
                val G = out Bool()
                val B = out Bool()
            }
            val Address = out Bits(5 bits)
            val Latch = out Bool()
            val Sclk = out Bool()
            val Blank = out Bool()
        }
    }
    noIoPrefix()
    
    val clk48Domain = ClockDomain.internal(name = "Core48",  frequency = FixedFrequency(48 MHz))
    val clk12Domain = ClockDomain.internal(name = "Core12",  frequency = FixedFrequency(12 MHz))
    val clk12Domain_reset = ClockDomain.internal(name = "Core12_reset",  frequency = FixedFrequency(12 MHz))

    val Core12_reset = new ClockingArea(clk12Domain_reset) {
        var reset = Reg(Bool) init (False)
        var rstCounter = CounterFreeRun(255)
        when(rstCounter.willOverflow){
            reset := True
        }
    }

    // //PLL Settings for 17.625MHz
    // val PLL_CONFIG = SB_PLL40_PAD_CONFIG(
    //     DIVR = B"0000", DIVF = B"0101110", DIVQ = B"101", FILTER_RANGE = B"001",
    //     FEEDBACK_PATH = "SIMPLE", PLLOUT_SELECT = "GENCLK", 
    //     DELAY_ADJUSTMENT_MODE_FEEDBACK = "FIXED", DELAY_ADJUSTMENT_MODE_RELATIVE = "FIXED", //NO DELAY
    //     FDA_FEEDBACK = B"0000", FDA_RELATIVE = B"0000", SHIFTREG_DIV_MODE = B"0", ENABLE_ICEGATE = False //NOT USED
    // ) 

    // //Define PLL
    // val PLL = new SB_PLL40_CORE(PLL_CONFIG)
    // //Setup signals of PLL
    // PLL.BYPASS := False
    // PLL.RESETB := True
    // PLL.REFERENCECLK := io.clk_12Mhz

    //Define the internal oscillator
    val intOSC = new IntOSC()
    intOSC.io.CLKHFEN := True
    intOSC.io.CLKHFPU := True
    
    //Connect the PLL output of 12Mhz to the 12MHz clock domain
    clk12Domain_reset.clock := io.clk_12Mhz
    clk12Domain_reset.reset := !io.reset_

    //Connect the PLL output of 12Mhz to the 12MHz clock domain
    clk12Domain.clock := io.clk_12Mhz
    clk12Domain.reset := clk12Domain_reset.reset

    // //Connect the PLL output of 17.625Mhz to the 17.625MHz clock domain
    // clk12Domain.clock := PLL.PLLOUTGLOBAL
    // clk12Domain.reset := !Core12.reset

    // //Connect the internal oscillator output to the 48MHz clock domain
    // clk48Domain.clock := intOSC.io.CLKHF
    // clk48Domain.reset := !Core12.reset

    val Core12 = new ClockingArea(clk12Domain)
    {
            val hub = new hub75_top()
            io.hub75 <> hub.io.hub75
    }
}

object Top_ICE40_Verilog extends App {
  Config.spinal.generateVerilog(new Top_ICE40())
}