package better.files

import java.util.concurrent.Executors.newFixedThreadPool
import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicInteger

import org.scalatest.concurrent.Eventually.{eventually, timeout}
import org.scalatest.refspec.RefSpec
import org.scalatest.time.{Seconds, Span}

import scala.concurrent.ExecutionContext
import scala.util.Random

class FileMonitor2Spec extends RefSpec {

  def `Multiple files modification events should be processed by the file monitor` = {
    val folder = File.newTemporaryDirectory()
    val file1 = File.newTemporaryFile(suffix = ".txt", parent = Some(folder))
    val file2 = File.newTemporaryFile(suffix = ".txt", parent = Some(folder))
    val file3 = File.newTemporaryFile(suffix = ".txt", parent = Some(folder))

    val counter = new AtomicInteger(0)

    val worker = ExecutionContext.fromExecutor(newFixedThreadPool(15 /* lots of head room */, factory))
    val watcher = new FileMonitor(folder)(worker) {
      override def onModify(file: File, count: Int) = { counter.incrementAndGet(); waitForEver() }
    }

    watcher.start()

    val touch: File => Runnable = file => () => { file.writeText(Random.nextString(5)); () }

    val executor = ExecutionContext.fromExecutor(newFixedThreadPool(3))
    List(file1, file2, file3).map(touch).foreach(executor.execute)

    eventually(timeout(Span(25, Seconds))) {
      assert(counter.get() == 3)
    }
  }

  def waitForEver() = while(!Thread.currentThread().isInterrupted) { Thread.`yield`() }

  val threadCount = new AtomicInteger(1)
  def factory = new ThreadFactory {
    def newThread(runnable: Runnable): Thread = {
      val thread = new Thread(runnable)
      thread.setName(s"better-files-worker-thread-${threadCount.getAndIncrement()}")
      thread
    }
  }
}