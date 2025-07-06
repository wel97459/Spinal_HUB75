package hub75

import spinal.core._
import spinal.lib._
import spinal.lib.fsm._
import spinal.core.sim._
import scala.util.control.Breaks
import  MySpinalHardware._

case class PWM_Test(val period: BigInt) extends Component {
    val io = new Bundle
    {
        val start = in Bool()
        val Wait = in Bool()
        val mask = out Bits(8 bits)
        val blank = out Bool()
        val done = out Bool()
    }

/***-Defines-***/    


/***-Registers-***/
    val counter = CounterUpDownSet(65536)
    val bitmask = Reg(Bits(8 bits)) init(0) 
    val done = Reg(Bool()) init(False)
    val waiting = Reg(Bool()) init(False)

/***-Wires-***/

/***-IO stuff-***/
    io.mask := bitmask
    io.done := done
    io.blank := False
/***-Streams-***/


/***-Blocks-***/


/***-Routing-***/

    
/***-LutChains-***/
    val bitmask_next = (bitmask === 0) ? B"10000000" | bitmask(0) ## bitmask(7 downto 1)

/***-Logic-***/
    when(io.start && done)
    {
        counter.setValue((bitmask << period).resize(16).asUInt)
        done := False
        bitmask := bitmask_next
    }elsewhen(bitmask === B"00000000"){
        bitmask := bitmask_next
        done := True
    }elsewhen(!io.Wait && counter === 0 && bitmask =/= 0){
        counter.setValue((bitmask << period).resize(16).asUInt)
        bitmask := bitmask_next
    }elsewhen(counter === 0 && bitmask === B"10000000"){
        done := True
    }elsewhen(counter > 0){
        io.blank := True
        counter.decrement()
    }

}


case class hub75_Test() extends Component {
    val io = new Bundle
    {
        val start = in Bool()
        val Wait = in Bool()
        val Latch = out Bool()
        val Sclk = out Bool()
        val X = out UInt(8 bits)
        val done = out Bool()
    }

/***-Defines-***/    


/***-Registers-***/
    val counter = CounterUpDownSet(256)
    val clk = Reg(Bool()) init(False)
    val running = Reg(Bool()) init(False)
    val done = Reg(Bool()) init(False)
/***-Wires-***/

/***-IO stuff-***/
    io.Latch := (counter === 0 && !io.Wait).rise()
    io.Sclk := clk
    io.done := done
    io.X := counter
/***-Streams-***/


/***-Blocks-***/


/***-Routing-***/

    
/***-LutChains-***/

/***-Logic-***/
    when(counter === 0 && io.start && !running)
    {
        done := False
        running := True
        counter.setValue(64)
    }elsewhen(counter =/= 0){
        when(clk){
            counter.decrement()
            clk := False
        }otherwise{
            clk := True
        }
    }elsewhen(!io.start && counter === 0){
        running := False
        done := True
    }otherwise{
        done := True
    }
}

case class hub75_top() extends Component {
    val io = new Bundle {
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

/***-Defines-***/    


/***-Registers-***/
    val a = Reg(UInt(8 bits)) init(0)


/***-Wires-***/

    val redValue = Bits(8 bits)
    val greenValue = Bits(8 bits)
    val blueValue = Bits(8 bits)

/***-Streams-***/

/***-Blocks-***/
    val glow = new PWM_Test(0)
    val hub = new hub75_Test()

    glow.io.start := False
    glow.io.Wait := False

    hub.io.start := False
    hub.io.Wait := False

/***-Routing-***/
    io.hub75.Sclk := hub.io.Sclk
    io.hub75.Latch := hub.io.Latch
    io.hub75.RGB0.R := (glow.io.mask & redValue).asBits =/= 0
    io.hub75.RGB0.G := (glow.io.mask & greenValue).asBits =/= 0
    io.hub75.RGB0.B := (glow.io.mask & blueValue).asBits =/= 0
    io.hub75.RGB1.R := False
    io.hub75.RGB1.G := False
    io.hub75.RGB1.B := False

    io.hub75.Blank := !glow.io.blank

    io.hub75.Address := a(4 downto 0).asBits
    
/***-LutChains-***/
    redValue := (64 - hub.io.X).asBits;
    greenValue := B"000" ## a(4 downto 0).asBits
    blueValue := 2;

/***-Logic-***/
    when(glow.io.done.rise()){
        a := a + 1
    }

    val InterfaceFMS = new StateMachine {
        /***-FMS-***/
        val Reset: State = new StateDelay(10) with EntryPoint {
            whenCompleted {
                goto(SendData)
            }
        }

        val SendData: State = new  State {
            whenIsActive{
                glow.io.Wait := True
                hub.io.start := True
                goto(SendDataWait)
            }
        }

        val SendDataWait: State = new  State {
            whenIsActive{
                glow.io.Wait := True
                when(hub.io.done){
                    goto(RunPWM)
                }
            }
        }

        val RunPWM: State = new  State {
            whenIsActive{
                glow.io.start := True
                goto(SendData)
            }
        }
    }

}

object Hub75Sim extends App {
    Config.sim.compile(hub75_top()).doSim { dut =>
        //Fork a process to generate the reset and the clock on the dut
        dut.clockDomain.forkStimulus(period = 10)
        var c = 0;
        var cc = 0;
        val loop = new Breaks;
        loop.breakable {
            while (true) {
                dut.clockDomain.waitRisingEdge()

                c += 1
                if(c > 65536){
                    loop.break;
                }
            }
        }
    }
}