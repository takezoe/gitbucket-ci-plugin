package io.github.gitbucket.ci

import java.sql.DriverManager

import io.github.gitbucket.solidbase.Solidbase
import io.github.gitbucket.solidbase.model.Module
import liquibase.database.core.{H2Database, MySQLDatabase, PostgresDatabase}
import org.scalatest.{FunSuite, Tag}

import scala.collection.JavaConverters._
import com.wix.mysql.EmbeddedMysql._
import com.wix.mysql.config.Charset
import com.wix.mysql.config.MysqldConfig._
import com.wix.mysql.distribution.Version._
import ru.yandex.qatools.embed.postgresql.PostgresStarter
import ru.yandex.qatools.embed.postgresql.config.AbstractPostgresConfig.{Credentials, Net, Storage, Timeout}
import ru.yandex.qatools.embed.postgresql.config.PostgresConfig
import ru.yandex.qatools.embed.postgresql.distribution.Version.Main.PRODUCTION

object ExternalDBTest extends Tag("ExternalDBTest")

class MigrationSpec extends FunSuite {
  val plugin = Class.forName("Plugin").newInstance().asInstanceOf[gitbucket.core.plugin.Plugin]

  test("Migration H2") {
    new Solidbase().migrate(
      DriverManager.getConnection("jdbc:h2:mem:test", "sa", "sa"),
      Thread.currentThread().getContextClassLoader(),
      new H2Database(),
      new Module(plugin.pluginId, plugin.versions.asJava)
    )
  }

  test("Migration MySQL", ExternalDBTest) {
    val config = aMysqldConfig(v5_7_latest)
      .withPort(3306)
      .withUser("sa", "sa")
      .withCharset(Charset.UTF8)
      .withServerVariable("log_syslog", 0)
      .withServerVariable("bind-address", "127.0.0.1")
      .build()

    val mysqld = anEmbeddedMysql(config)
      .addSchema("gitbucket")
      .start()

    try {
      new Solidbase().migrate(
        DriverManager.getConnection("jdbc:mysql://localhost:3306/gitbucket?useSSL=false", "sa", "sa"),
        Thread.currentThread().getContextClassLoader(),
        new MySQLDatabase(),
        new Module(plugin.pluginId, plugin.versions.asJava)
      )
    } finally {
      mysqld.stop()
    }
  }

  test("Migration PostgreSQL", ExternalDBTest) {
    val runtime = PostgresStarter.getDefaultInstance()
    val config = new PostgresConfig(
      PRODUCTION,
      new Net("localhost", 5432),
      new Storage("gitbucket"),
      new Timeout(),
      new Credentials("sa", "sa")
    )

    val exec = runtime.prepare(config)
    val process = exec.start()

    try {
      new Solidbase().migrate(
        DriverManager.getConnection("jdbc:postgresql://localhost:5432/gitbucket", "sa", "sa"),
        Thread.currentThread().getContextClassLoader(),
        new PostgresDatabase(),
        new Module(plugin.pluginId, plugin.versions.asJava)
      )
    } finally {
      process.stop()
    }
  }
}
