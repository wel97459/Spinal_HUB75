package hub75

import spinal.core._
import spinal.lib._
import spinal.core.sim._
import scala.util.control.Breaks
import  MySpinalHardware._
case class PWM_Test() extends Component {
    val io = new Bundle
    {
        val value = in Bits(8 bits)
        val led = out Bool()
        val cycle = out Bool()
    }

/***-Defines-***/    


/***-Registers-***/
    val counter = CounterUpDownSet(4096)
    val bitmask = Reg(Bits(8 bits)) init(B"10000000") 


/***-Wires-***/

/***-IO stuff-***/
io.led := (io.value & bitmask) =/= 0
io.cycle := False
/***-Streams-***/


/***-Blocks-***/


/***-Routing-***/

    
/***-LutChains-***/
val bitmask_next = bitmask(0) ## bitmask(7 downto 1)

/***-Logic-***/

    when(counter === 0)
    {
        bitmask := bitmask_next
        counter.setValue((bitmask ## B"1111").asUInt)
        io.cycle := (bitmask === B"10000000")
    }otherwise{
        counter.decrement()
    }

}

object Hub75Sim extends App {
    Config.sim.compile(PWM_Test()).doSim { dut =>
        //Fork a process to generate the reset and the clock on the dut
        dut.clockDomain.forkStimulus(period = 10)
        var c = 0;
        var cc = 0;
        val loop = new Breaks;

        loop.breakable {
            dut.io.value #= 10
            while (true) {
                dut.clockDomain.waitRisingEdge()

                c += 1
                if(c > 4096*2){
                    loop.break;
                }
            }
        }
    }
}