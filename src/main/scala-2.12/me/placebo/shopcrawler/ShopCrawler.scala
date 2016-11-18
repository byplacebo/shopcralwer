package me.placebo.shopcrawler

import java.util.concurrent.TimeUnit

import akka.actor._
import akka.pattern.ask
import akka.util.Timeout
import me.placebo.shopcrawler.Checker.{CheckExist, Exist, NotExist}
import me.placebo.shopcrawler.Crawler.Crawl
import me.placebo.shopcrawler.MerchantCrawler.{CrawlOwner, CrawlShop}
import me.placebo.shopcrawler.ProductCrawler.{GrawlProduct, GrawReviews}
import me.placebo.shopcrawler.Storage.AddCrawledUrl

import scala.concurrent.duration._

object Storage {

  sealed trait StorageMessage

  case class AddCrawledUrl(url: String) extends StorageMessage

}

class Storage extends Actor {
  var urls = List.empty[String]

  override def receive: Receive = {
    case AddCrawledUrl(url) =>
      println(s"storage: crawled url: $url")
      urls = url :: urls
  }
}

object Checker {

  sealed trait CheckerMessage

  case class CheckExist(url: String) extends CheckerMessage

  sealed trait CheckerResponse

  case class Exist(url: String) extends CheckerResponse

  case class NotExist(url: String) extends CheckerResponse

  def props = Props[Checker]
}

class Checker extends Actor {
  val urls = List(
    "https://11st.co.kr"
    , "https://22st.co.kr"
  )

  override def receive: Receive = {
    case CheckExist(url) =>
      if (urls.contains(url)) {
        println(s"exist url in storage: $url")
        sender() ! Exist(url)
      } else {
        println(s"not exist url at storage: $url")
        sender() ! NotExist(url)
      }
  }
}

object MerchantCrawler {

  sealed trait MerchantCrawlMessage

  case class CrawlShop(url: String) extends MerchantCrawlMessage

  case class CrawlOwner(url: String) extends MerchantCrawlMessage

  def props = Props[MerchantCrawler]

}

class MerchantCrawler extends Actor {
  override def receive: Receive = {
    case CrawlShop(url) => println(s"getting shop information $url")
    case CrawlOwner(url) => println(s"getting owner information $url")
  }
}

object ProductCrawler {

  sealed trait ProductCrawlMessage

  case class GrawlProduct(url: String) extends ProductCrawlMessage

  case class GrawReviews(url: String) extends ProductCrawlMessage

  def props = Props[ProductCrawler]
}

class ProductCrawler extends Actor {
  override def receive: Receive = {
    case GrawlProduct(url) => println(s"getting product information from $url")
    case GrawReviews(url) => println(s"getting reviews information from $url")
  }
}

object Crawler {

  sealed trait CrawlMessage

  case class Crawl(url: String) extends CrawlMessage

  case object GetUrlToCrawl extends CrawlMessage

  def props(merchantCrawler: ActorRef, productCrawler: ActorRef, checker: ActorRef) = Props(new Crawler(merchantCrawler, productCrawler, checker))
}


class Crawler(merchantCrawler: ActorRef, productCrawler: ActorRef, checker: ActorRef) extends Actor {

  import scala.concurrent.ExecutionContext.Implicits.global

  implicit val timeout = Timeout(5 seconds)

  override def receive: Receive = {
    case Crawl(url) =>
      println(s"get requesting crawled website: $url")
      checker ? Checker.CheckExist(url) map {
        case Checker.Exist(s) =>
          println(s"skip crawling url already be in storage: $s")
        case Checker.NotExist(s) =>
          println(s"crawling url message: $s")
          merchantCrawler ! CrawlShop(url)
          merchantCrawler ! CrawlOwner(url)
          productCrawler ! GrawlProduct(url)
          productCrawler ! GrawReviews(url)
      }
    case msg => println(s"unknown message: $msg")
  }
}

object ShopCrawler extends App {
  val system = ActorSystem("ShopCrawler")

  val merchantCrawler = system.actorOf(MerchantCrawler.props, "merchantCrawler")
  val productCrawler = system.actorOf(ProductCrawler.props, "productCrawler")
  val checker = system.actorOf(Checker.props, "checker")

  val crawler = system.actorOf(Crawler.props(merchantCrawler, productCrawler, checker), "Crawler")

  crawler ! Crawl("http://11st.co.kr")

  crawler ! Crawl("https://11st.co.kr")

  TimeUnit.SECONDS.sleep(3)

  system.terminate()
}
