/*
*  SelfSimilarity.scala
*  (Strugatzki)
*
*  Copyright (c) 2011-2013 Hanns Holger Rutz. All rights reserved.
*
*  This software is free software; you can redistribute it and/or
*  modify it under the terms of the GNU General Public License
*  as published by the Free Software Foundation; either
*  version 2, june 1991 of the License, or (at your option) any later version.
*
*  This software is distributed in the hope that it will be useful,
*  but WITHOUT ANY WARRANTY; without even the implied warranty of
*  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
*  General Public License for more details.
*
*  You should have received a copy of the GNU General Public
*  License (gpl.txt) along with this software; if not, write to the Free Software
*  Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
*
*
*  For further information, please contact Hanns Holger Rutz at
*  contact@sciss.de
*/

package de.sciss.strugatzki

import java.io.File
import xml.{XML, NodeSeq}
import language.implicitConversions
import concurrent.{ExecutionContext, Promise}

object SelfSimilarity extends ProcessorCompanion {
   type PayLoad = Unit

   object ColorScheme {
      def apply( logicalName: String ) : ColorScheme = logicalName match {
         case GrayScale.logicalName       => GrayScale
         case PsychoOptical.logicalName   => PsychoOptical
      }

      def all : Seq[ ColorScheme ] = Seq( GrayScale, PsychoOptical )
      def names : Seq[ String ] = all.map( _.logicalName )
   }
   sealed trait ColorScheme {
      def logicalName: String
   }

   /**
    * Plain gray scale.
    */
   case object GrayScale extends ColorScheme {
      val logicalName = "gray"
   }
   /**
    * Psycho-optically optimized scheme originally taken from
    * Niklas Werner's Sonasound program. Uses a combination of
    * hues and brightnesses that produces as perceptually very
    * regular gradient. It goes from black over violet towards
    * yellow and finally white.
    */
   case object PsychoOptical extends ColorScheme {
      val logicalName = "psycho"
   }

   /**
    * All durations, spans and spacings are given in sample frames
    * with respect to the sample rate of the audio input file.
    */
   sealed trait ConfigLike {
      /**
       * The database folder is merely used to retrieve the normalization file,
       * given that `normalize` is `true`.
       */
      def databaseFolder : File

      /**
       * The XML file holding the extractor parameters corresponding to the
       * audio input file. The audio input file's feature vector output file
       * is determined from this meta file.
       */
      def metaInput: File

      /**
       * The file to which the self similarity matrix image is written.
       */
      def imageOutput: File

      /**
       * An option which restricts the calculation to a given span within the
       * input file. If `None`, the whole file is considered.
       */
      def span: Option[ Span ]

      /**
       * The size of the sliding window over which the features are correlated.
       * That is, for a length of 1.0 second (given in sample frames, hence
       * 44100 for a sample rate of 44100 Hz), at any given point in time,
       * 0.5 seconds left of that point are correlated with 0.5 seconds right
       * of that point.
       */
      def corrLen: Long

      /**
       * A decimation factor to produce smaller image size. A factor of 1 means
       * each frame step is performed, a factor of 2 means every second frame
       * is skipped, a factor of 3 means only one in three frames is considered,
       * and so forth.
       */
      def decimation: Int

      /**
       * The balance between the feature of loudness curve and spectral composition (MFCC).
       * A value of 0.0 means the segmentation is only performed by considering the
       * spectral features, and a value of 1.0 means the segmentation is taking only
       * the loudness into consideration. Values in between give a measure that takes
       * both features into account with the given priorities.
       */
      def temporalWeight: Float

      /**
       * The color scheme to use for the image. Either of `GrayScale` and `PsychoOptical`
       */
      def colors: ColorScheme

      /**
       * A warp factor (exponent) applied to the cross correlations before conversion to
       * a color value. Somewhat like a gamma correction. Values smaller than 1 produce
       * brighter images, values greater than 1 produce darker images.
       */
      def colorWarp: Float

      /**
       * The ceiling cross correlation value corresponding to the maximally bright color.
       * Should be less than or equal to 1, and greater than zero. The smaller the value,
       * the earlier clipping occurs, and the more the colors are 'dragged' towards
       * brighter values.
       */
      def colorCeil: Float

      /**
       * Whether the color scale should be inverted (`true`) or not (`false`).
       */
      def colorInv: Boolean

      /**
       * Whether to apply normalization to the features (recommended)
       * */
      def normalize : Boolean

      final def pretty: String = {
         "Config(\n   databaseFolder = " + databaseFolder +
                  "\n   metaInput      = " + metaInput +
                  "\n   imageOutput    = " + imageOutput +
                  "\n   span           = " + span +
                  "\n   corrLen        = " + corrLen +
                  "\n   decimation     = " + decimation +
                  "\n   temporalWeight = " + temporalWeight +
                  "\n   colors         = " + colors +
                  "\n   colorWarp      = " + colorWarp +
                  "\n   colorCeil      = " + colorCeil +
                  "\n   colorInv       = " + colorInv +
                  "\n   normalize      = " + normalize + "\n)"
      }
   }

   object ConfigBuilder {
      def apply( settings: Config ) : ConfigBuilder = {
         val sb = Config()
         sb.read( settings )
         sb
      }
   }
   final class ConfigBuilder private[SelfSimilarity] () extends ConfigLike {
      /**
       * The database folder defaults to `database` (relative path)
       */
      var databaseFolder      = new File( "database" ) // Strugatzki.defaultDir
      /**
       * The correlation input file's extractor meta data file defaults
       * to `input_feat.xml` (relative path)
       */
      var metaInput           = new File( "input_feat.xml" )
      /**
       * The image output file defaults to `output_selfsim.png` (relative path)
       */
      var imageOutput         = new File( "output_selfsim.png" )
      /**
       * The optional span restriction defaults to `None`.
       */
      var span                = Option.empty[ Span ]
      /**
       * The correlation length defaults to 44100 sample frames
       * (or 1.0 seconds at 44.1 kHz sample rate).
       */
      var corrLen             = 44100L
      /**
       * The default decimation factor is `1` (no decimation).
       */
      var decimation          = 1
      /**
       * The temporal weight defaults to 0.5.
       */
      var temporalWeight      = 0.5f
      /**
       * The default color scheme is psycho-optical.
       */
      var colors              = PsychoOptical: ColorScheme
      /**
       * The default color warp is `1.0`.
       */
      var colorWarp           = 1.0f
      /**
       * The default color ceiling is `1.0`.
       */
      var colorCeil           = 1.0f
      /**
       * The colors are not inverted by default.
       */
      var colorInv            = false
      /**
       * The feature vector normalization flag defaults to `true`.
       */
      var normalize           = true

      def build: Config = Impl( databaseFolder, metaInput, imageOutput, span, corrLen, decimation, temporalWeight,
                            colors, colorWarp, colorCeil, colorInv, normalize )

      def read( settings: Config ) {
         databaseFolder = settings.databaseFolder
         metaInput      = settings.metaInput
         imageOutput    = settings.imageOutput
         span           = settings.span
         corrLen        = settings.corrLen
         decimation     = settings.decimation
         temporalWeight = settings.temporalWeight
         colors         = settings.colors
         colorWarp      = settings.colorWarp
         colorCeil      = settings.colorCeil
         colorInv       = settings.colorInv
         normalize      = settings.normalize
      }

     private final case class Impl( databaseFolder: File, metaInput: File, imageOutput: File, span: Option[ Span ],
                                corrLen: Long, decimation: Int, temporalWeight: Float,
                                colors: ColorScheme, colorWarp: Float, colorCeil: Float, colorInv: Boolean,
                                normalize: Boolean )
     extends Config {
       override def productPrefix = "Config"

        private def spanToXML( span: Span ) =
  <span>
     <start>{span.start}</start>
     <stop>{span.stop}</stop>
  </span>

        def toXML =
  <selfsimilarity>
     <database>{databaseFolder.getPath}</database>
     <input>{metaInput.getPath}</input>
     <output>{imageOutput.getPath}</output>
     {span match { case Some( s ) => <span>{spanToXML( s ).child}</span>; case _ => Nil }}
     <corr>{corrLen}</corr>
     <decimation>{decimation}</decimation>
     <weight>{temporalWeight}</weight>
     <colors>{colors.logicalName}</colors>
     <colorWarp>{colorWarp}</colorWarp>
     <colorCeil>{colorCeil}</colorCeil>
     <colorInv>{colorInv}</colorInv>
     <normalize>{normalize}</normalize>
  </selfsimilarity>
     }
   }

   object Config {
     def apply(): ConfigBuilder = new ConfigBuilder

      implicit def build( sb: ConfigBuilder ) : Config = sb.build

      private def spanFromXML( xml: NodeSeq ) : Span = {
         val start   = (xml \ "start").text.toLong
         val stop    = (xml \ "stop").text.toLong
         Span( start, stop )
      }

      def fromXMLFile( file: File ) : Config = fromXML( XML.loadFile( file ))
      def fromXML( xml: NodeSeq ) : Config = {
         val sb = Config()
         sb.databaseFolder = new File( (xml \ "database").text )
         sb.metaInput      = new File( (xml \ "input").text )
         sb.imageOutput    = new File( (xml \ "output").text )
         sb.span           = {
            val e = xml \ "span"
            if( e.isEmpty ) None else Some( spanFromXML( e ))
         }
         sb.corrLen        = (xml \ "corr").text.toLong
         sb.decimation     = (xml \ "decimation").text.toInt
         sb.temporalWeight = (xml \ "weight").text.toFloat
         sb.colors         = ColorScheme( (xml \ "colors").text )
         sb.colorWarp      = (xml \ "colorWarp").text.toFloat
         sb.colorCeil      = (xml \ "colorCeil").text.toFloat
         sb.colorInv       = (xml \ "colorInv").text.toBoolean
         sb.normalize      = (xml \ "normalize").text.toBoolean
         sb.build
      }
   }
  sealed trait Config extends ConfigLike {
    def toXML: xml.Node
  }

  protected def defaultConfig: Config = Config()

  /**
    * Create a new process to calculate the self similarity matrix of a sound file and save it as an image file.
    *
    * @param config         the settings for the process (e.g. input and output files)
    * @param observer         function observing the progress of the running process
    * @return                 the self similarity matrix image generating process (to be started)
    */
  protected def create(config: Config, observer: SelfSimilarity.Observer,
                       promise: Promise[SelfSimilarity.PayLoad])
                      (implicit exec: ExecutionContext): Processor[SelfSimilarity.PayLoad, Config] =
     new impl.SelfSimilarity(config, observer, promise)
}
