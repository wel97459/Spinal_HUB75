package hub75

import spinal.core._
import spinal.lib._
import spinal.lib.fsm._
import spinal.lib.blackbox.lattice.ice40._

import MySpinalHardware._

import spinal.core.sim._
import scala.util.control.Breaks



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

        //val led_red = out Bool()
        val led_green = out Bool()
        //val led_blue = out Bool()
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
        spi.io.output.ready := False
        io.spi <> spi.io.spi
        
        val led = Reg(Bool()) init(False)
        val load = Reg(Bool()) init(False)
        val loadAddress = Reg(Bool()) init(False)
        val lastPayload = Reg(Bits(8 bits)) init(0)
        val filpBuffer = Reg(Bool()) init(False)

        val hub = new hub75_top(64, 64, 0x0800)
        io.hub75 <> hub.io.hub75

        val hubAccess = False 
        val AddrLast = RegNext(hub.io.RamInterface.Address)
        val ReadyLast = RegNext(hub.io.RamInterface.Ready && hubAccess)

        val progAddr = CounterUpDownSet(0x8000)

        val spram = new SPRAM256KA()
        spram.io.POWEROFF := True
        spram.io.SLEEP := False
        spram.io.STANDBY := False
        spram.io.CHIPSELECT := True

        spram.io.WREN := False
        spram.io.MASKWREN := progAddr(0) ? B"1100" | B"0011" 
        spram.io.DATAIN := spi.io.output.payload(7 downto 0) ## spi.io.output.payload(7 downto 0)

        val hubAddr = hub.io.RamInterface.Address(13 downto 0)// | (filpBuffer ? B"14'h2000" | B"14'h0000")

        spram.io.ADDRESS := hubAccess ? hubAddr | progAddr(14 downto 1).asBits
        
        hub.io.RamInterface.DataIn := spram.io.DATAOUT
        hub.io.RamInterface.Valid := (AddrLast === hub.io.RamInterface.Address) && ReadyLast


        hub.io.brightness := 3
        io.led_green := !led


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
                    when(!spi.io.instruction){
                        load := False
                        loadAddress := False
                    }
                    when(hub.io.clear){
                        goto(Reset)
                    }elsewhen(hub.io.RamInterface.Ready){
                        goto(HubAccess)
                    }elsewhen(spi.io.output.valid){
                        when(load){
                            spram.io.WREN := True
                        }
                        when(spi.io.output.payload === B"9'h1A0"){
                            loadAddress := True
                        }
                        goto(CheckSerial)
                    }
                }
            }

            val CheckSerial: State = new State
            {
                whenIsActive{
                    lastPayload := spi.io.output.payload(7 downto 0)
                    when(spi.io.output.payload === B"9'h1A0"){
                        loadAddress := True
                        progAddr.setValue(0)
                    }
                    when(loadAddress){
                        progAddr.increment()
                        when(progAddr === 1){
                            progAddr.setValue((lastPayload(6 downto 0) ## spi.io.output.payload(7 downto 0)).asUInt)
                            loadAddress := False
                            load := True
                        }
                    }
                    when(load)
                    {
                        progAddr.increment()
                    }
                    spi.io.output.ready := True
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

case class testspi() extends Component{
    val io = new Bundle{

        val spi = new Bundle
        {
            val sck = in Bool()
            val ss = in Bool()
            val mosi = in Bool()
            val miso = out Bool()
        }
    }

    val spi = SlaveSPI()
    spi.io.input.valid := False
    spi.io.output.ready := False
    io.spi <> spi.io.spi
    
    val led = Reg(Bool()) init(False)
    val load = Reg(Bool()) init(False)
    val loadAddress = Reg(Bool()) init(False)
    val lastPayload = Reg(Bits(8 bits)) init(0)
    
    val wen = False
    val progAddr = CounterUpDownSet(0x8000)

    val InterfaceFMS = new StateMachine {
        /***-FMS-***/
        val Reset: State = new State with EntryPoint {
            whenIsActive{
                goto(Start)
            }
        }

        val Start: State = new State
        {
            whenIsActive{
                when(!spi.io.instruction){
                    load := False
                    loadAddress := False
                }

                when(spi.io.output.valid){
                    when(load){
                        wen := True
                    }
                    when(spi.io.output.payload === B"9'h1A0"){
                        loadAddress := True
                    }
                    goto(CheckSerial)
                }
            }
        }

        val CheckSerial: State = new State
        {
            whenIsActive{
                lastPayload := spi.io.output.payload(7 downto 0)
                when(spi.io.output.payload === B"9'h1A0"){
                    loadAddress := True
                    progAddr.setValue(0)
                }
                when(loadAddress){
                    progAddr.increment()
                    when(progAddr === 1){
                        progAddr.setValue((lastPayload(6 downto 0) ## spi.io.output.payload(7 downto 0)).asUInt)
                        loadAddress := False
                        load := True
                    }
                }
                spi.io.output.ready := True
                goto(Start)
            }
        }
    }
}

object Top_ICE40_Verilog extends App {
  Config.spinal.generateVerilog(new Top_ICE40())
}

object SlaveSPIFMS_Test extends App {
    Config.sim.compile(testspi()).doSim { dut =>
        //Fork a process to generate the reset and the clock on the dut
        dut.clockDomain.forkStimulus(period = 83)
        var c = 0;
        var cc = 0;
        var a=0;
        val b = Array(0xa0, 0x10, 0x50, 0x55);
        val loop = new Breaks;
        dut.io.spi.ss #= true
        loop.breakable {
            while (true) {
                dut.clockDomain.waitRisingEdge()
                if(c > 50){
                    dut.io.spi.ss #= false
                }
                if( c % 4 >= 2 && c > 100){
                    dut.io.spi.sck #= true
                }else{
                    dut.io.spi.sck #= false
                }
                if( c % 4 == 2 && c > 100){

                    dut.io.spi.mosi #= ((b(a) << cc) & 0x80) == 0x80
                    if(cc >= 7 && a<3){
                        cc=0;
                        a+=1;
                    }else{
                        cc+=1;
                    }
                }
                c += 1
                if(c > 65536){
                    loop.break;
                }
            }
        }
    }
}