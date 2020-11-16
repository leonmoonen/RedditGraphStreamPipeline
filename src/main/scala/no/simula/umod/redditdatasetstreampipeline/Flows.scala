package no.simula.umod.redditdatasetstreampipeline

import java.nio.charset.StandardCharsets

import akka.NotUsed
import akka.stream.alpakka.csv.scaladsl.{CsvFormatting, CsvQuotingStyle}
import akka.stream.scaladsl.{Flow, Framing}
import akka.util.ByteString
import no.simula.umod.redditdatasetstreampipeline.model.JsonFormats._
import no.simula.umod.redditdatasetstreampipeline.model.ModelEntity.ModelEntity
import no.simula.umod.redditdatasetstreampipeline.model.{Comment, ModelEntity, Submission, ToCsv}
import spray.json.DefaultJsonProtocol.jsonFormat2
import spray.json._


object Flows {
  implicit val commentFormat: RootJsonFormat[Comment] = jsonFormat2(Comment)

  def ndJsonToObj(entity: ModelEntity) : Flow[ByteString, ToCsv, NotUsed] = {
    val f = Flow[ByteString]
      .via(Framing.delimiter( //chunk the inputs up into actual lines of text
        ByteString("\n"),
        maximumFrameLength = Int.MaxValue,
        allowTruncation = true))

    entity match {
      case ModelEntity.SubmissionEntity => f.map(_.utf8String.parseJson.convertTo[Submission])
      case ModelEntity.CommentEntity => f.map(_.utf8String.parseJson.convertTo[Comment])
      case _ => throw new NotImplementedError("ndJson for this type is not implemented.")
    }
  }

  /**
   * Takes NdJson ByteStrings and converts them to Submission objects
   */
  val ndJsonToSubmission: Flow[ByteString, ToCsv, NotUsed] = Flow[ByteString]
    .via(Framing.delimiter( //chunk the inputs up into actual lines of text
      ByteString("\n"),
      maximumFrameLength = Int.MaxValue,
      allowTruncation = true))
    // Possible but costlier alternative to new lines would be
    // To scan the stream for json objects
    // .via(JsonFraming.objectScanner(Int.MaxValue))'
    .map(_.utf8String.parseJson.convertTo[Submission]) // Create json objects




  /**
   * Converts an object to a line of CSV byte string
   */
  val objectToCsv: Flow[ToCsv, ByteString, NotUsed] = Flow[ToCsv]
    .map(s => s.toCsvSeq) // Get sequence of field values
    .via(CsvFormatting.format( // Create csv line
      CsvFormatting.Comma,
      CsvFormatting.DoubleQuote,
      CsvFormatting.Backslash,
      "\n",
      CsvQuotingStyle.Required,
      StandardCharsets.UTF_8,
      None))
}
