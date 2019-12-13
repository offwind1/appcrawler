package com.testerhome.appcrawler


import java.io.{File, StringWriter}
import java.nio.charset.{Charset, StandardCharsets}
import java.util
import java.util.Base64
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.xpath.{XPathConstants, XPathFactory}

import com.fasterxml.jackson.databind.{DeserializationFeature, ObjectMapper, SerializationFeature}
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.github.tototoshi.csv.CSVReader
import com.jayway.jsonpath.{Configuration, JsonPath}
import com.sun.org.apache.xml.internal.dtm.ref.DTMNodeList
import net.lightbody.bmp.core.har.Har
import net.minidev.json.JSONArray
import org.apache.commons.io.IOUtils

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer
import scala.reflect.ClassTag
import scala.reflect.ClassTag
import scala.reflect._
import org.jsoup.Jsoup
import org.jsoup.nodes.{Document, Element}
import org.jsoup.select.Elements
import org.w3c.dom.NodeList
import us.codecraft.xsoup.Xsoup

import scala.collection.JavaConversions._
import scala.io.Source
import collection.JavaConverters._

/**
  * Created by seveniruby on 16/8/13.
  */
object TData {

  private val factory=DocumentBuilderFactory.newInstance
  private val builder=factory.newDocumentBuilder()
  private val xpathObject=XPathFactory.newInstance().newXPath()

  private val defaultJsonConfig = Configuration.defaultConfiguration()
  defaultJsonConfig.addOptions(com.jayway.jsonpath.Option.DEFAULT_PATH_LEAF_TO_NULL)
  //defaultJsonConfig.addOptions(com.jayway.jsonpath.Option.ALWAYS_RETURN_LIST)
  //JsonPath.using(defaultJsonConfig)

  def toYaml(data: Any): String = {
    val mapper = new ObjectMapper(new YAMLFactory())
    mapper.registerModule(DefaultScalaModule)
    mapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false)
    mapper.writerWithDefaultPrettyPrinter().writeValueAsString(data)
  }

  def fromYaml[T: ClassTag](data: String): T = {
    val mapper = new ObjectMapper(new YAMLFactory())
    mapper.registerModule(DefaultScalaModule)
    mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    mapper.readValue(data, classTag[T].runtimeClass.asInstanceOf[Class[T]])
  }


  def toJson(data: Any): String = {
    val mapper = new ObjectMapper()
    mapper.registerModule(DefaultScalaModule)
    mapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false)
    mapper.writerWithDefaultPrettyPrinter().writeValueAsString(data)
  }


  def fromJson[T: ClassTag](str: String): T = {
    val mapper = new ObjectMapper()
    mapper.registerModule(DefaultScalaModule)
    mapper.readValue(str, classTag[T].runtimeClass.asInstanceOf[Class[T]])
  }
  def pretty(jsonString: String): String ={
    val mapper = new ObjectMapper()
    mapper.registerModule(DefaultScalaModule)
    mapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false)

    val jsonObject=mapper.readValue(jsonString, classOf[java.lang.Object])
    mapper.writerWithDefaultPrettyPrinter().writeValueAsString(jsonObject)
  }

  def toXML(data: Any, root:String="xml"): String = {
    val mapper = new XmlMapper()
    mapper.registerModule(com.fasterxml.jackson.module.scala.DefaultScalaModule)
    //mapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false)
    //mapper.registerModule(DefaultScalaModule)
    mapper.writerWithDefaultPrettyPrinter().withRootName(root).writeValueAsString(data)
  }


  def fromXML[T: ClassTag](str: String): T = {
    val mapper = new XmlMapper()
    mapper.registerModule(com.fasterxml.jackson.module.scala.DefaultScalaModule)
    mapper.readValue(str, classTag[T].runtimeClass.asInstanceOf[Class[T]])
  }

  def fromHTML(str: String): Map[String, Any] = {
    val node = Jsoup.parse(str)

    def lift(node: Element): Map[String, Any] = node match {
      case doc: Document =>
        Map[String, Any](
          "head" -> lift(doc.head),
          "body" -> lift(doc.body)
        )

      case doc: Element => {
        val children: Elements = doc.children
        val attributes =
          doc.attributes.asList map { attribute =>
            attribute.getKey -> attribute.getValue
          } toMap

        Map(
          "tag" -> doc.tagName,
          "text" -> doc.ownText,
          "attributes" -> attributes,
          "children" -> children.map(element => lift(element))
        )

      }
    }

    lift(node)
  }


  //扁平化
  def flatten(data: Map[String, Any]): mutable.Map[String, Any] = {
    val stack = new mutable.Stack[String]()
    val result = mutable.Map[String, Any]()

    def loop(dataKV: scala.collection.Map[String, Any]): Unit = {

      dataKV.foreach(data => {
        stack.push(data._1)
        data match {
          case (key: String, valueMap: scala.collection.Map[String, _]) => {
            val tag = valueMap.getOrElse("tag", "").toString
            val key = tag.split('.').lastOption.getOrElse(tag)
            if (tag.nonEmpty) {
              stack.push(key)
            }

            valueMap.foreach(kv => {
              loop(scala.collection.Map(kv._1 -> kv._2))
            })

            if (tag.nonEmpty) {
              stack.pop()
            }

          }
          case (key: String, values: Seq[_]) => {
            var index = 0
            values.foreach(value => {
              loop(Map(index.toString -> value))
              index += 1
            })
          }
          case (key, value: Any) => {
            result(stack.reverse.mkString(".")) = value
          }
          case (key, null) => {
            result(stack.reverse.mkString(".")) = null
          }

        }
        stack.pop()
      })
    }

    loop(data)
    result
  }

  def toSchema(content:String): mutable.Map[String, String]={
    val map=flatten(from(content))
    val mapNew=mutable.Map[String, String]()
    map.map{case (k, v)=>{
      v match {
        case null => mapNew(k)="null"
        case _ => mapNew(k)=v.getClass.getSimpleName
      }

    }
    }
    mapNew
  }

  //从扁平化结构重新拼装为完整结构
  def pushToMap(origin: mutable.Map[String, Any], keys: Array[String], value: Any): Unit = {
    if (keys.length == 1) {
      origin(keys.head) = value
    } else {
      if (origin.contains(keys.head)) {
        if (origin(keys.head).isInstanceOf[mutable.Map[String, Any]] == false) {
          origin(keys.head) = mutable.Map[String, Any]()
        } else {
        }
      } else {
        origin(keys.head) = mutable.Map[String, Any]()
      }
      pushToMap(origin(keys.head).asInstanceOf[mutable.Map[String, Any]], keys.tail, value)
    }
  }


  //两个nested的结构合并
  def deepMerge[K](map: Map[K, _], that: Map[K, _]): Map[K, _] = {
    (for (k <- map.keys ++ that.keys) yield {
      val newValue =
        (map.get(k), that.get(k)) match {
          case (Some(v), None) => v
          case (None, Some(v)) => v
          case (Some(v1), Some(v2)) => {
            (v1, v2) match {
              case (v1: Map[K, _], v2: Map[K, _]) => deepMerge(v1, v2)
              case (v1: List[_], v2: List[_]) => v1 ++ v2
              case (v1: Array[_], v2: Array[_]) => v1 ++ v2
              case (v1, null) => v1
              case _ => v2
            }
          }
        }
      k -> newValue
    }).toMap
  }

  //从文本中给出结构化的解析结果
  def from(content:String): Map[String, Any] ={
    content.trim.take(10) match {
      case json if json.contains("{") =>{
        fromJson[Map[String, Any]](content)
      }
      case html if html.toLowerCase.contains("<html>") => {
        fromHTML(content)
      }
      case xml if xml.contains("<") =>{
        val root=builder.parse(IOUtils.toInputStream(content)).getDocumentElement.getTagName
        val elements=fromXML[Map[String,Any]](content)
        Map(root->elements)
      }
      case _ => {
        Map("raw"->content)
      }
    }
  }

  //todo: 支持单个值获取
  //给出jsonpath的结果
  def jsonPath(raw:String, path:String): Any ={
    val res=JsonPath.using(defaultJsonConfig).parse(raw).read[Any](path)
    res match{
      case array: JSONArray => {
        array.toList
      }
      case x: Any => {
        res
      }
    }
    //JsonPath.using(defaultJsonConfig).parse(raw).read[JSONArray](path).map(_.toString).toList
  }

  //解析xpath并给出结果
  private def xpath2(raw:String, path:String): Any ={
    val doc=Jsoup.parse(raw)
    Xsoup.compile(path).evaluate(doc).get
  }

  private def xpathList(raw:String, path:String, encoding:String="UTF-8"): Any = {
    val doc=builder.parse(IOUtils.toInputStream(raw, encoding))
    val array=xpathObject.compile(path).evaluate(doc, XPathConstants.NODESET).asInstanceOf[NodeList]
    0.until(array.getLength).map(i=>{
      val children=array.item(i).getChildNodes
      0.until(children.getLength).map(j=>children.item(j).getNodeValue)
    }).flatten.toList
  }
  def xpathSingle(raw:String, path:String, encoding:String="UTF-8"): Any ={
    val doc=builder.parse(IOUtils.toInputStream(raw, encoding))
    xpathObject.compile(path).evaluate(doc, XPathConstants.STRING)
  }
  def xpath(raw:String, path:String):Any={
    path match {
      case single if List("[", "(").exists(c=>single.contains(c)) => xpathSingle(raw, path)
      case list if List("//").exists(c=>list.contains(c)) => xpathList(raw, path)
      case _ => xpathSingle(raw, path)
    }
  }

  //用于windows上坑爹的编码问题
  def setEncoding(): Unit ={
    System.setProperty("file.encoding", "UTF-8");
    val charset = classOf[Charset].getDeclaredField("defaultCharset")
    charset.setAccessible(true)
    charset.set(null, null)
  }


  def har2string(har:Har): String ={
    val writer=new StringWriter()
    har.writeTo(writer)
    writer.toString
  }


  def decodeBase64(raw: String): String = {
    return new String(Base64.getDecoder.decode(raw))
  }

  def encodeBase64(raw: String): String = {
    val str = Base64.getEncoder.encodeToString(raw.getBytes(StandardCharsets.UTF_8))
    return str
  }

  def fromCSV(file:String): Array[java.util.Map[String, String]] ={
    CSVReader.open(new File(getClass.getResource(file).getPath)).allWithHeaders().map(_.asJava).toArray
  }
  def dataDriver(templateFile:String, dataFile:String, key:String="template"): Array[java.util.Map[String, String]] ={
    val path=if(templateFile.head=='/'){
      templateFile.takeRight(templateFile.size-1)
    }else{
      templateFile
    }
    val content=Source.fromResource(path).mkString
    val csv=CSVReader.open(new File(getClass.getResource(dataFile).getPath)).allWithHeaders()
    csv.map(m=>{
      var contentNew=content
      m.keys.foreach(key=>{
        contentNew=contentNew.replace(s"$${${key}}", m(key))
      })
      (m++Map(key->contentNew)).asJava
    }).toArray
  }
  def dataDriver(templateFile:String, dataFile:String): Array[java.util.Map[String, String]] ={
    dataDriver(templateFile, dataFile, "template")
  }

  //todo: 递归解析
  def toHashMap(someObject: Any): Any ={
    someObject match {
      case kv: Map[String, _] => {
        val h=new java.util.HashMap[String, Any]()
        kv.foreach{
          case (k,v)=> {
            h.put(k, toHashMap(v))
          }
        }
        h
      }
      case list: List[_]=> {
        list.map(item=>{
          toHashMap(item)
        }).toArray
      }
      case array: Array[_]=> {
        array.map(item=>{
          toHashMap(item)
        })
      }
      case _ => {
        someObject
      }
    }
  }

}
