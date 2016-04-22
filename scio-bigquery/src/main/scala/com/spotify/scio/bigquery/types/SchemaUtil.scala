/*
 * Copyright 2016 Spotify AB.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.spotify.scio.bigquery.types

import java.util.{List => JList}

import com.google.api.services.bigquery.model.{TableFieldSchema, TableSchema}

import scala.collection.JavaConverters._

private object SchemaUtil {

  def toPrettyString(schema: TableSchema, name: String, indent: Int): String = {
    getCaseClass(schema.getFields, name, indent)
  }

  private def getRawType(tfs: TableFieldSchema, indent: Int): (String, Seq[String]) = {
    val name = tfs.getType match {
      case "INTEGER" => "Int"
      case "FLOAT" => "Double"
      case "BOOLEAN" => "Boolean"
      case "STRING" => "String"
      case "TIMESTAMP" => "Instant"
      case "RECORD" => NameProvider.getUniqueName(tfs.getName)
      case t => throw new IllegalArgumentException(s"Type: $t not supported")
    }
    if (tfs.getType == "RECORD") {
      val nested = getCaseClass(tfs.getFields, name, indent)
      (name, Seq(nested))
    } else {
      (name, Seq.empty)
    }
  }

  private def getFieldType(tfs: TableFieldSchema,
                           indent: Int): (String, Seq[String]) = {
    val (rawType, nested) = getRawType(tfs, indent)
    val fieldType = tfs.getMode match {
      case "NULLABLE" | null => "Option[" + rawType + "]"
      case "REQUIRED" => rawType
      case "REPEATED" => "List[" + rawType + "]"
    }
    (fieldType, nested)
  }

  private def getCaseClass(fields: JList[TableFieldSchema], name: String,
                           indent: Int): String = {
    val xs = fields.asScala
      .map { f =>
        val (fieldType, nested) = getFieldType(f, indent)
        (f.getName + ": " + fieldType, nested)
      }
    val lines = xs.map(_._1)
    val nested = xs.flatMap(_._2)

    val sb = StringBuilder.newBuilder
    sb.append(s"case class $name(")
    if (indent > 0) {
      sb.append("\n")
    }
    val body = if (indent > 0) {
      val w = " " * indent
      lines.map(w + _).mkString(",\n")
    } else {
      lines.mkString(", ")
    }
    sb.append(body)
    sb.append(")")
    (sb.toString() +: nested).mkString("\n")
  }

}
