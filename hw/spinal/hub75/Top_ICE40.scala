package hub75

import spinal.core._
import spinal.lib._
import spinal.lib.fsm._
import spinal.lib.blackbox.lattice.ice40._

import MySpinalHardware._




//Hardware definition
class Top_ICE40() extends Component {
    val io = new Bundle {
        val reset_ = in Bool()
        val clk_12Mhz = in Bool() //12Mhz CLK

        // val serial_txd = out Bool()
        // val serial_rxd = in Bool()

        val spi = new Bundle
        {
            val sck = in Bool()
            val ss = in Bool()
            val mosi = in Bool()
            val miso = out Bool()
        }

        val led_red = out Bool()
        val led_green = out Bool()
        val led_blue = out Bool()
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
    val clk12Domain = ClockDomain.internal(name = "Core12",  frequency = FixedFrequency(19.875 MHz))
    val clk12Domain_reset = ClockDomain.internal(name = "Core12_reset",  frequency = FixedFrequency(12 MHz))

    val Core12_reset = new ClockingArea(clk12Domain_reset) {
        var reset = Reg(Bool) init (False)
        var rstCounter = CounterFreeRun(255)
        when(rstCounter.willOverflow){
            reset := True
        }
    }

    // //PLL Settings for 17.625MHz
    val PLL_CONFIG = SB_PLL40_PAD_CONFIG(
        DIVR = B"0000", DIVF = B"0110100", DIVQ = B"101", FILTER_RANGE = B"001",
        FEEDBACK_PATH = "SIMPLE", PLLOUT_SELECT = "GENCLK", 
        DELAY_ADJUSTMENT_MODE_FEEDBACK = "FIXED", DELAY_ADJUSTMENT_MODE_RELATIVE = "FIXED", //NO DELAY
        FDA_FEEDBACK = B"0000", FDA_RELATIVE = B"0000", SHIFTREG_DIV_MODE = B"0", ENABLE_ICEGATE = False //NOT USED
    ) 

    // //Define PLL
    val PLL = new SB_PLL40_CORE(PLL_CONFIG)
    // //Setup signals of PLL
    PLL.BYPASS := False
    PLL.RESETB := True
    PLL.REFERENCECLK := io.clk_12Mhz

    //Define the internal oscillator
    val intOSC = new IntOSC()
    intOSC.io.CLKHFEN := True
    intOSC.io.CLKHFPU := True
    
    //Connect the PLL output of 12Mhz to the 12MHz clock domain
    clk12Domain_reset.clock := io.clk_12Mhz
    clk12Domain_reset.reset := !io.reset_

    //Connect the PLL output of 12Mhz to the 12MHz clock domain
    clk12Domain.clock := PLL.PLLOUTGLOBAL
    clk12Domain.reset := clk12Domain_reset.reset

    // //Connect the PLL output of 17.625Mhz to the 17.625MHz clock domain
    // clk12Domain.clock := PLL.PLLOUTGLOBAL
    // clk12Domain.reset := !Core12.reset

    // //Connect the internal oscillator output to the 48MHz clock domain
    // clk48Domain.clock := intOSC.io.CLKHF
    // clk48Domain.reset := !Core12.reset

    val Core12 = new ClockingArea(clk12Domain)
    {
        val spi = SlaveSPI()
        spi.io.input.valid := False
        spi.io.output.ready := True
        io.spi <> spi.io.spi
        val led = Reg(Bool()) init(False)
        when(spi.io.output.payload === B"9'h130")
        {
            led := True
        }elsewhen(spi.io.output.payload === B"9'h131"){
            led := False
        }

        val filpBuffer = Reg(Bool()) init(False)

        val hub = new hub75_top(128, 64)
        io.hub75 <> hub.io.hub75

        val hubAccess = False 
        val AddrLast = RegNext(hub.io.RamInterface.Address)
        val ReadyLast = RegNext(hub.io.RamInterface.Ready && hubAccess)

        // val prog = new ProgrammingInterface(115200)
        // prog.io.UartRX := io.serial_rxd
        // io.serial_txd := prog.io.UartTX
        // prog.io.FlagIn := 0
        // prog.io.RamInterface.DataIn := 0
        // prog.io.keys.ready := True

        val serialData = StreamFifo(
            dataType = Bits(24 bits),
            depth    = 8
        )

        val serialDataIn = Stream(Bits(24 bits))
        val serialDataOut = Stream(Bits(24 bits))

        val spram = new SPRAM256KA()
        spram.io.POWEROFF := True
        spram.io.SLEEP := False
        spram.io.STANDBY := False
        spram.io.CHIPSELECT := True

        spram.io.WREN := False
        spram.io.MASKWREN := serialDataOut.payload(8) ? B"1100" | B"0011" 
        spram.io.DATAIN := serialDataOut.payload(7 downto 0) ## serialDataOut.payload(7 downto 0)

        val hubAddr = hub.io.RamInterface.Address(13 downto 0)// | (filpBuffer ? B"14'h2000" | B"14'h0000")
        val progAddr = serialDataOut.payload(22 downto 9) //| (!filpBuffer ? B"14'h2000" | B"14'h0000")

        spram.io.ADDRESS := hubAccess ? hubAddr | progAddr
        
        hub.io.RamInterface.DataIn := spram.io.DATAOUT
        hub.io.RamInterface.Valid := (AddrLast === hub.io.RamInterface.Address) && ReadyLast

        serialData.io.push << serialDataIn
        serialData.io.pop >> serialDataOut

        // serialDataIn.payload := prog.io.RamInterface.Address ## prog.io.RamInterface.DataOut
        // serialDataIn.valid := serialDataIn.ready && prog.io.RamInterface.Write
        serialDataIn.payload := 0
        serialDataIn.valid := False
        serialDataOut.ready := False

        hub.io.brightness := 3
        io.led_green := !led
        io.led_red := io.spi.ss
        io.led_blue := io.spi.mosi


        // when(prog.io.FlagOut(2).rise())
        // {
        //     filpBuffer := !filpBuffer
        // }

        val InterfaceFMS = new StateMachine {
            /***-FMS-***/
            val Reset: State = new State with EntryPoint {
                whenIsActive{
                    when(!hub.io.clear){
                        goto(Start)
                    }
                }
            }

            val Start: State = new State
            {
                whenIsActive{
                    when(hub.io.clear){
                        goto(Reset)
                    }elsewhen(hub.io.RamInterface.Ready){
                        goto(HubAccess)
                    }elsewhen(serialDataOut.valid){
                        spram.io.WREN := True
                        goto(CheckSerial)
                    }
                }
            }

            val CheckSerial: State = new State
            {
                whenIsActive{
                    serialDataOut.ready := True
                    goto(Start)
                }
            }

            val HubAccess: State = new State
            {
                whenIsActive{
                    when(hub.io.clear){
                        goto(Reset)
                    }elsewhen(hub.io.RamInterface.Ready){
                        hubAccess := True
                    }otherwise{
                        goto(Start)
                    }

                }
            }
        }
    }
}

object Top_ICE40_Verilog extends App {
  Config.spinal.generateVerilog(new Top_ICE40())
}