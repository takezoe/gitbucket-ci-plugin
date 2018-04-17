package io.github.gitbucket.ci.api

import java.time._
import java.util.Date

import gitbucket.core.api.JsonFormat._

import scala.util.Try
import org.json4s._
import org.json4s.jackson.Serialization


object JsonFormat {

  val jsonFormats = Serialization.formats(NoTypeHints).preservingEmptyValues +
    // TODO This serializer should define in core JsonFormat
    new CustomSerializer[Date](format =>
      ({ case JString(s) =>
        Try(Date.from(Instant.parse(s))).getOrElse(throw new MappingException("Can't convert " + s + " to Date"))
      },
      { case x: Date =>
        JString(OffsetDateTime.ofInstant(x.toInstant, ZoneId.of("UTC")).format(parserISO))
      })
    )

  /**
   * convert object to json string
   */
  def apply(obj: AnyRef)(implicit c: Context): String =
    Serialization.write(obj)(jsonFormats + apiPathSerializer(c) + sshPathSerializer(c))

}
