package cc.spray.json

import DefaultJsonProtocol._

import org.specs2.mutable.Specification

class JsonLensesSpec extends Specification with SpecHelpers {
  import JsonLenses._

  val n = field("n")

  "Lenses" should {
    "access" in {
      "field" in {
        "existing" in {
          """{"n": 2}""" extract "n".get[Int] must be_==(2)
        }
        "missing" in {
          """{"n": 2}""" extract "z".get[Int] must throwAn[Exception]("""Expected field 'z' in '{"n":2}'""")
        }
        "wrong type" in {
          """{"n": 2}""" extract "n".get[String] must throwA[DeserializationException]("Expected String as JsString, but got 2")
        }
      }
      "field of member" in {
        """{"n": {"b": 4}}""" extract ("n" / "b").get[Int] must be_==(4)
      }
      "element of array" in {
        "existing" in {
          """["a", "b", 2, 5, 8, 3]""" extract element(3).get[Int] must be_==(5)
        }
        "out of bounds" in {
          """["a", "b", 2, 5, 8, 3]""" extract element(38).get[Int] must throwAn[IndexOutOfBoundsException]("Too little elements in array: [\"a\",\"b\",2,5,8,3] size: 6 index: 38")
        }
      }
      "finding an element" in {
        "in a homogenous array" in {
          "if type matches" in {
            """[18, 23, 2, 5, 8, 3]""" extract JsonLenses.find(JsonLenses.value.is[Int](_ < 4)).get[Int] must beSome(2)
          }
          "if type is wrong" in {
            """[18, 23, 2, 5, 8, 3]""" extract JsonLenses.find(JsonLenses.value.is[String](_ < "unknown")).get[Int] must beNone
          }
        }
        "in an imhomogenous array" in {
          """["a", "b", 2, 5, 8, 3]""" extract JsonLenses.find(JsonLenses.value.is[Int](_ < 4)).get[Int] must beSome(2)
          """["a", "b", 2, 5, 8, 3]""" extract JsonLenses.find(JsonLenses.value.is[String](_ == "unknown")).get[Int] must beNone
        }
        "nested finding" in {
          val lens = JsonLenses.find("a".is[Int](_ == 12)) / "b" / "c" andThen JsonLenses.find(JsonLenses.value.is[Int](_ == 5))

          "existing" in {
            """[{"a": 12, "b": {"c": [2, 5]}}, 13]""" extract lens.get[Int] must beSome(5)
          }
          "missing in first find" in {
            """[{"a": 2, "b": {"c": [5]}}, 13]""" extract lens.get[Int] must beNone
          }
          "missing in second find" in {
            """[{"a": 2, "b": {"c": [7]}}, 13]""" extract lens.get[Int] must beNone
          }
        }
      }
    }

    "modify" in {
      "set field" in {
        "existing" in {
          """{"n": 12}""" update (n ! set(23)) must be_json( """{"n": 23}""")
        }
        "missing" in {
          """{"n": {"b": 4}}""" update ("n" / "c" ! set(23)) must be_json( """{"n": {"b": 4, "c": 23}}""")
        }
      }
      "update field" in {
        "existing" in {
          """{"n": 12}""" update (n ! updated[Int](_ + 1)) must be_json( """{"n": 13}""")
        }
        "wrong type" in {
          """{"n": 12}""" update (n ! updated[String](_ + "test")) must throwA[DeserializationException]("Expected String as JsString, but got 12")
        }
        "missing" in {
          """{"n": 12}""" update (field("z") ! updated[Int](_ + 1)) must throwAn[Exception]("""Expected field 'z' in '{"n":12}'""")
        }
      }
      "set field of member" in {
        """{"n": {"b": 4}}""" update ("n" / "b" ! set(23)) must be_json( """{"n": {"b": 23}}""")
      }
      "update field of member" in {
        "existing" in {
          """{"n": {"b": 4}}""" update ("n" / "b" ! updated[Int](1 +)) must be_json( """{"n": {"b": 5}}""")
        }
        "parent missing" in {
          """{"x": {"b": 4}}""" update ("n" / "b" ! updated[Int](1 +)) must throwAn[Exception]("""Expected field 'n' in '{"x":{"b":4}}'""")
        }
      }
      "set element of array" in {
        """["a", "b", 2, 5, 8, 3]""" update (element(3) ! set(35)) must be_json("""["a", "b", 2, 35, 8, 3]""")
      }
      "change a found element" in {
        "in a homogenuous array" in {
          "if found" in {
            """[12, 39, 2, 5, 8, 3]""" update (JsonLenses.find(JsonLenses.value.is[Int](_ < 4)) ! set("test")) must be_json(
              """[12, 39, "test", 5, 8, 3]""")
          }
          "if not found" in {
            """[12, 39, 2, 5, 8, 3]""" update (JsonLenses.find(JsonLenses.value.is[Int](_ == 434)) ! set("test")) must be_json(
              """[12, 39, 2, 5, 8, 3]""")
          }
        }
        "in an inhomogenuous array" in {
          """["a", "b", 2, 5, 8, 3]""" update (JsonLenses.find(JsonLenses.value.is[Int](_ < 4)) ! set("test")) must be_json(
            """["a", "b", "test", 5, 8, 3]"""
          )
        }
        "nested" in {
          val lens = JsonLenses.find("a".is[Int](_ == 12)) / "b" / "c" andThen JsonLenses.find(JsonLenses.value.is[Int](_ == 5))

          "existing" in {
            """[{"a": 12, "b": {"c": [2, 5]}}, 13]""" update (lens ! set(42)) must be_json("""[{"a": 12, "b": {"c": [2, 42]}}, 13]""")
          }
          "missing in first find" in {
            """[{"a": 2, "b": {"c": [5]}}, 13]""" update (lens ! set(42)) must be_json("""[{"a": 2, "b": {"c": [5]}}, 13]""")
          }
          "missing in second find" in {
            """[{"a": 2, "b": {"c": [7]}}, 13]""" update (lens ! set(42)) must be_json("""[{"a": 2, "b": {"c": [7]}}, 13]""")
          }
        }
      }
    }
  }
}


