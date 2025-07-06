package hub75

import spinal.core._
import spinal.lib._
import spinal.core.sim._
import scala.util.control.Breaks
import  MySpinalHardware._

case class PWM_Test(val pad: BigInt, val period: BigInt) extends Component {
    val io = new Bundle
    {
        val start = in Bool()
        val mask = out Bits(8 bits)
        val blank = out Bool()
        val done = out Bool()
    }

/***-Defines-***/    


/***-Registers-***/
    val counter = CounterUpDownSet(65536)
    val bitmask = Reg(Bits(9 bits)) init(0) 
    val done = Reg(Bool()) init(False)
    val waiting = Reg(Bool()) init(False)

/***-Wires-***/

/***-IO stuff-***/
io.mask := bitmask(7 downto 0)
io.done := done
io.blank := False
/***-Streams-***/


/***-Blocks-***/


/***-Routing-***/

    
/***-LutChains-***/
val bitmask_next = (bitmask === 0) ? B"100000000" | B"0" ## bitmask(8 downto 1)

/***-Logic-***/
    when(counter === 0 && io.start)
    {
        when(bitmask_next === B"100000000"){
            counter.setValue(pad)
        }otherwise{
            counter.setValue((bitmask << period).resize(16).asUInt)
        }
        done := False
        bitmask := bitmask_next
    }elsewhen(counter === 0){
        done := True
    }otherwise{
        when(counter > 0 && bitmask_next =/= B"100000000"){
            io.blank := True
        }
        counter.decrement()
    }

}


case class hub75_Test() extends Component {
    val io = new Bundle
    {
        val start = in Bool()
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
    io.Latch := (counter === 0).rise()
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
    val glow = new PWM_Test(128, 0)
    val a = Reg(UInt(8 bits)) init(0)

    val hub = new hub75_Test()
    io.hub75.Sclk := hub.io.Sclk
    io.hub75.Latch := hub.io.Latch
    
    hub.io.start := False
    when(glow.io.done){
        a := a + 1
        hub.io.start := True
    }

    glow.io.start := False
    when(hub.io.done && glow.io.done){
        glow.io.start := True
    }


    val redValue = Bits(8 bits)
    val greenValue = Bits(8 bits)
    val blueValue = Bits(8 bits)

    redValue := 1
    greenValue := 0
    blueValue := 0

    io.hub75.RGB0.R := (glow.io.mask & redValue).asUInt === 0
    io.hub75.RGB0.G := (glow.io.mask & greenValue).asUInt === 0
    io.hub75.RGB0.B := (glow.io.mask & blueValue).asUInt === 0
    io.hub75.RGB1.R := False
    io.hub75.RGB1.G := False
    io.hub75.RGB1.B := False

    io.hub75.Blank := !glow.io.blank

    io.hub75.Address := a(4 downto 0).asBits
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