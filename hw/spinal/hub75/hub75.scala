package hub75

import spinal.core._
import spinal.lib._
import spinal.core.sim._
import scala.util.control.Breaks
import  MySpinalHardware._

case class PWM_Test(val pad: BigInt, val period: BigInt) extends Component {
    val io = new Bundle
    {
        val startCycle = in Bool()
        val mask = out Bits(8 bits)
        val cycle = out Bool()
    }

/***-Defines-***/    


/***-Registers-***/
    val counter = CounterUpDownSet(65536)
    val bitmask = Reg(Bits(9 bits)) init(0) 


/***-Wires-***/

/***-IO stuff-***/
io.mask := bitmask(7 downto 0)
io.cycle := False
/***-Streams-***/


/***-Blocks-***/


/***-Routing-***/

    
/***-LutChains-***/
val bitmask_next = (bitmask === 0) ? B"100000000" | B"0" ## bitmask(8 downto 1)

/***-Logic-***/
    when(counter === 0 && io.startCycle)
    {
        when(bitmask_next === B"100000000"){
            counter.setValue(pad)
            io.cycle := True
        }otherwise{
            counter.setValue((bitmask << period).resize(16).asUInt)
        }
        bitmask := bitmask_next
    }otherwise{
        counter.decrement()
    }

}


case class hub75_Test() extends Component {
    val io = new Bundle
    {
        val Start = in Bool()
        val Latch = out Bool()
        val Sclk = out Bool()
    }

/***-Defines-***/    


/***-Registers-***/
    val counter = CounterUpDownSet(256)
    val clk = Reg(Bool()) init(False)
    val done = Reg(Bool()) init(False)
/***-Wires-***/

/***-IO stuff-***/
    io.Latch := (counter === 0).rise()
    io.Sclk := clk
/***-Streams-***/


/***-Blocks-***/


/***-Routing-***/

    
/***-LutChains-***/

/***-Logic-***/
    when(counter === 0 && io.Start && !done)
    {
        done := True
        counter.setValue(64)
    }elsewhen(counter =/= 0){
        when(clk){
            counter.decrement()
            clk := False
        }otherwise{
            clk := True
        }
    }elsewhen(!io.Start){
        done := False
    }
}

object Hub75Sim extends App {
    Config.sim.compile(hub75_Test()).doSim { dut =>
        //Fork a process to generate the reset and the clock on the dut
        dut.clockDomain.forkStimulus(period = 10)
        var c = 0;
        var cc = 0;
        val loop = new Breaks;
        dut.io.Start #= true
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