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

    val redValue0 = Bits(8 bits)
    val greenValue0 = Bits(8 bits)
    val blueValue0 = Bits(8 bits)

    val redValue1 = Bits(8 bits)
    val greenValue1 = Bits(8 bits)
    val blueValue1 = Bits(8 bits)

/***-Streams-***/

/***-Blocks-***/
    val glow = new PWM_Test(0)
    val hub = new hub75_Test()

    val ball = new BasicBall(64, 63) 
    
    glow.io.start := False
    glow.io.Wait := False

    hub.io.start := False
    hub.io.Wait := False

    ball.io.reset := False
/***-Routing-***/
    val ballU = False 
    val ballL = False
    val X = 64 - hub.io.X

    when(X === ball.io.X && a(4 downto 0) === ball.io.Y && ball.io.Y < 32){
        ballU := True
    }

    when(X === ball.io.X && a(4 downto 0) === (ball.io.Y - 32) && ball.io.Y > 31){
        ballL := True
    }

    io.hub75.Sclk := hub.io.Sclk
    io.hub75.Latch := hub.io.Latch
    
    io.hub75.RGB0.R := (glow.io.mask & redValue0).asBits =/= 0
    io.hub75.RGB0.G := (glow.io.mask & greenValue0).asBits =/= 0
    io.hub75.RGB0.B := (glow.io.mask & blueValue0).asBits =/= 0

    io.hub75.RGB1.R := (glow.io.mask & redValue1).asBits =/= 0
    io.hub75.RGB1.G := (glow.io.mask & greenValue1).asBits =/= 0
    io.hub75.RGB1.B := (glow.io.mask & blueValue1).asBits =/= 0

    io.hub75.Blank := !glow.io.blank

    io.hub75.Address := a(4 downto 0).asBits
    
/***-LutChains-***/
    when(ballU){
        redValue0 := 255
        greenValue0 := 255
        blueValue0 := 255
    }otherwise{
        redValue0 := 2
        greenValue0 := 2
        blueValue0 := 2
    }

    when(ballL){
        redValue1 := 255
        greenValue1 := 255
        blueValue1 := 255
    }otherwise{
        redValue1 := 2
        greenValue1 := 2
        blueValue1 := 2
    }

/***-Logic-***/
    when(glow.io.done.rise()){
        a := a + 1
    }

    ball.io.update := (a === 64).rise()

    val InterfaceFMS = new StateMachine {
        /***-FMS-***/
        val Reset: State = new StateDelay(10) with EntryPoint {
            whenIsActive{
                ball.io.reset := True
            }
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

case class BasicBall(val SX: BigInt, val SY: BigInt) extends Component
{
    val io = new Bundle {
        val update = in Bool()
        val reset = in Bool()
        val X = out UInt(8 bits)
        val Y = out UInt(8 bits)
    }

    val ballX = Reg(UInt(8 bits)) init(0)
    val ballY = Reg(UInt(8 bits)) init(10)
    val ballXDir = Reg(Bool) init(True)
    val ballYDir = Reg(Bool) init(True)

    io.X := ballX
    io.Y := ballY
    when(io.reset){
        ballX := 0
        ballY := 10
        ballXDir := True
        ballYDir := True
    }elsewhen(io.update.fall()){
        when(ballX < 1){
            ballXDir := True
        } elsewhen(ballX >= SX){
            ballXDir := False
        }

        when(ballY < 1) {
            ballYDir := True
        } elsewhen(ballY >= SY){
            ballYDir := False
        }
    }elsewhen(io.update.rise()){
        when(ballXDir) {
            ballX := ballX + 1
        } otherwise (ballX := ballX - 1)

        when(ballYDir) {
            ballY := ballY + 1
        } otherwise (ballY := ballY - 1)
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
