<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">
  <!-- templates -->
  <!-- row -->
  <xsl:template match="row">
    <div>
      <xsl:attribute name="class">
        <xsl:text>row</xsl:text>
        <xsl:if test="@class">
          <xsl:text> </xsl:text>
          <xsl:value-of select="@class"/>
        </xsl:if>
      </xsl:attribute>
      <xsl:apply-templates select="column"/>
    </div>
  </xsl:template>
  <!-- row -->
  <!-- column -->
  <xsl:template match="column[not(@lg|@md|@sm|@xs)]">
    <div>
      <xsl:choose>
        <xsl:when test="@event or @showevent">
          <xsl:attribute name="class">
            <xsl:text>col-sm-12 sect</xsl:text>
            <xsl:if test="@push">
              <xsl:text> col-sm-push-</xsl:text>
              <xsl:value-of select="@push"/>
            </xsl:if>
            <xsl:if test="@pull">
              <xsl:text> col-sm-pull-</xsl:text>
              <xsl:value-of select="@pull"/>
            </xsl:if>
          </xsl:attribute>
          <xsl:attribute name="data-showevent">
            <xsl:choose>
              <xsl:when test="@event">
                <xsl:value-of select="@event"/>
              </xsl:when>
              <xsl:otherwise>
                <xsl:value-of select="@showevent"/>
              </xsl:otherwise>
            </xsl:choose>
          </xsl:attribute>
          <xsl:if test="@value or @showvalue">
            <xsl:attribute name="data-showarg">
              <xsl:choose>
                <xsl:when test="@value">
                  <xsl:value-of select="@value"/>
                </xsl:when>
                <xsl:otherwise>
                  <xsl:value-of select="@showvalue"/>
                </xsl:otherwise>
              </xsl:choose>
            </xsl:attribute>
          </xsl:if>
        </xsl:when>
        <xsl:otherwise>
          <xsl:attribute name="class">
            <xsl:text>col-sm-12</xsl:text>
            <xsl:if test="@push">
              <xsl:text> col-sm-push-</xsl:text>
              <xsl:value-of select="@push"/>
            </xsl:if>
            <xsl:if test="@pull">
              <xsl:text> col-sm-pull-</xsl:text>
              <xsl:value-of select="@pull"/>
            </xsl:if>
          </xsl:attribute>
        </xsl:otherwise>
      </xsl:choose>
      <xsl:apply-templates/>
    </div>
  </xsl:template>
  <xsl:template match="column[@lg|@md|@sm|@xs]">
    <div>
      <xsl:choose>
        <xsl:when test="@event or @showevent">
          <xsl:attribute name="class">
            <xsl:if test="@xs">
              <xsl:text>col-xs-</xsl:text>
              <xsl:value-of select="@xs"/>
              <xsl:text> </xsl:text>
            </xsl:if>
            <xsl:if test="@sm">
              <xsl:text>col-sm-</xsl:text>
              <xsl:value-of select="@sm"/>
              <xsl:text> </xsl:text>
            </xsl:if>
            <xsl:if test="@md">
              <xsl:text>col-md-</xsl:text>
              <xsl:value-of select="@md"/>
              <xsl:text> </xsl:text>
            </xsl:if>            
            <xsl:if test="@lg">
              <xsl:text>col-lg-</xsl:text>
              <xsl:value-of select="@lg"/>
            </xsl:if>
            <xsl:text> sect</xsl:text>
            <xsl:if test="@push">
              <xsl:text> col-sm-push-</xsl:text>
              <xsl:value-of select="@push"/>
            </xsl:if>
            <xsl:if test="@pull">
              <xsl:text> col-sm-pull-</xsl:text>
              <xsl:value-of select="@pull"/>
            </xsl:if>
          </xsl:attribute>
          <xsl:attribute name="data-showevent">
            <xsl:choose>
              <xsl:when test="@event">
                <xsl:value-of select="@event"/>
              </xsl:when>
              <xsl:otherwise>
                <xsl:value-of select="@showevent"/>
              </xsl:otherwise>
            </xsl:choose>
          </xsl:attribute>
          <xsl:if test="@value or @showvalue">
            <xsl:attribute name="data-showarg">
              <xsl:choose>
                <xsl:when test="@value">
                  <xsl:value-of select="@value"/>
                </xsl:when>
                <xsl:otherwise>
                  <xsl:value-of select="@showvalue"/>
                </xsl:otherwise>
              </xsl:choose>
            </xsl:attribute>
          </xsl:if>
        </xsl:when>
        <xsl:otherwise>
          <xsl:attribute name="class">
            <xsl:if test="@xs">
              <xsl:text>col-xs-</xsl:text>
              <xsl:value-of select="@xs"/>
              <xsl:text> </xsl:text>
            </xsl:if>
            <xsl:if test="@sm">
              <xsl:text>col-sm-</xsl:text>
              <xsl:value-of select="@sm"/>
              <xsl:text> </xsl:text>
            </xsl:if>
            <xsl:if test="@md">
              <xsl:text>col-md-</xsl:text>
              <xsl:value-of select="@md"/>
              <xsl:text> </xsl:text>
            </xsl:if>
            <xsl:if test="@lg">
              <xsl:text>col-lg-</xsl:text>
              <xsl:value-of select="@lg"/>
            </xsl:if>
            <xsl:if test="@push">
              <xsl:text> col-sm-push-</xsl:text>
              <xsl:value-of select="@push"/>
            </xsl:if>
            <xsl:if test="@pull">
              <xsl:text> col-sm-pull-</xsl:text>
              <xsl:value-of select="@pull"/>
            </xsl:if>
          </xsl:attribute>
        </xsl:otherwise>
      </xsl:choose>
      <xsl:apply-templates/>
    </div>
  </xsl:template>
  <!-- column -->
  <!-- title / subtitle -->
  <xsl:template name="title_body">
    <xsl:if test="@showevent">
      <xsl:attribute name="class">
        <xsl:text>sect</xsl:text>
      </xsl:attribute>
      <xsl:attribute name="data-showevent">
        <xsl:value-of select="@showevent"/>
      </xsl:attribute>
      <xsl:if test="@showvalue">
        <xsl:attribute name="data-showarg">
          <xsl:value-of select="@showvalue"/>
        </xsl:attribute>
      </xsl:if>
    </xsl:if>
    <xsl:if test="@event">
      <xsl:attribute name="data-event">
        <xsl:value-of select="@event"/>
      </xsl:attribute>
    </xsl:if>
    <xsl:value-of select="current()"/>
  </xsl:template>
  <xsl:template match="title">
    <xsl:choose>
      <xsl:when test="@size">
        <xsl:element name="h{@size}">
          <xsl:call-template name="title_body"/>
        </xsl:element>
      </xsl:when>
      <xsl:otherwise>
        <h4>
          <xsl:call-template name="title_body"/>
        </h4>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>
  <xsl:template match="subtitle">
    <xsl:choose>
      <xsl:when test="@size">
        <xsl:element name="h{@size}">
          <xsl:call-template name="title_body"/>
        </xsl:element>
      </xsl:when>
      <xsl:otherwise>
        <h5>
          <xsl:call-template name="title_body"/>
        </h5>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>
  <!-- title / subtitle -->
  <!-- text -->
  <xsl:template match="text">
    <p>
      <xsl:apply-templates select="icon"/>
      <xsl:if test="@showevent">
        <xsl:attribute name="class">
          <xsl:text>sect</xsl:text>
        </xsl:attribute>
        <xsl:attribute name="data-showevent">
          <xsl:value-of select="@showevent"/>
        </xsl:attribute>
        <xsl:if test="@showvalue">
          <xsl:attribute name="data-showarg">
            <xsl:value-of select="@showvalue"/>
          </xsl:attribute>
        </xsl:if>
      </xsl:if>
      <xsl:if test="@event">
        <xsl:attribute name="data-event">
          <xsl:value-of select="@event"/>
        </xsl:attribute>
      </xsl:if>
      <xsl:value-of select="current()"/>
    </p>
  </xsl:template>
  <!-- text -->
  <!-- button -->
  <xsl:template match="button[not(@type)]">
    <a href="#" type="button">
      <xsl:if test="(@confirm or @confirmtext)">
        <xsl:attribute name="data-confirm">
          <xsl:choose>
            <xsl:when test="@confirm"><xsl:value-of select="@confirm"/></xsl:when>
            <xsl:otherwise>true</xsl:otherwise>
          </xsl:choose>
        </xsl:attribute>
      </xsl:if>
      <xsl:if test="@confirmtitle">
        <xsl:attribute name="data-confirmtitle">
          <xsl:value-of select="@confirmtitle"/>
        </xsl:attribute>
      </xsl:if>
      <xsl:if test="@confirmtext">
        <xsl:attribute name="data-confirmtext">
          <xsl:value-of select="@confirmtext"/>
        </xsl:attribute>
      </xsl:if>
      <xsl:attribute name="class">
        <xsl:choose>
          <xsl:when test="@class">btn <xsl:value-of select="@class"/></xsl:when>
          <xsl:otherwise>btn btn-default</xsl:otherwise>
        </xsl:choose>
        <xsl:if test="@showevent">
          <xsl:text> sect</xsl:text>
        </xsl:if>
        <xsl:if test="badge|partialbadge|signal">
          <xsl:text> haschild</xsl:text>
        </xsl:if>
      </xsl:attribute>
      <xsl:if test="@event or @action or @join">
        <xsl:choose>
          <xsl:when test="@join">
            <xsl:attribute name="data-event">
              <xsl:value-of select="@join"/>
            </xsl:attribute>
            <xsl:attribute name="data-action">
              <xsl:value-of select="@join"/>
            </xsl:attribute>
          </xsl:when>
          <xsl:otherwise>
            <xsl:if test="@event">
              <xsl:attribute name="data-event">
                <xsl:value-of select="@event"/>
              </xsl:attribute>
            </xsl:if>
            <xsl:if test="@action">
              <xsl:attribute name="data-action">
                <xsl:value-of select="@action"/>
              </xsl:attribute>
            </xsl:if>
          </xsl:otherwise>
        </xsl:choose>
        <xsl:attribute name="data-class-on">
          <xsl:choose>
            <xsl:when test="@class-on"><xsl:value-of select="@class-on"/></xsl:when>
            <xsl:otherwise>btn-primary</xsl:otherwise>
          </xsl:choose>
        </xsl:attribute>
      </xsl:if>
      <xsl:if test="@showevent">
        <xsl:attribute name="data-showevent">
          <xsl:value-of select="@showevent"/>
        </xsl:attribute>
        <xsl:if test="@showvalue">
          <xsl:attribute name="data-showarg">
            <xsl:value-of select="@showvalue"/>
          </xsl:attribute>
        </xsl:if>
      </xsl:if>
      <xsl:if test="@arg">
        <xsl:attribute name="data-arg">
          <xsl:value-of select="@arg"/>
        </xsl:attribute>
      </xsl:if>
      <xsl:if test="@arg-on">
        <xsl:attribute name="data-arg-on">
          <xsl:value-of select="@arg-on"/>
        </xsl:attribute>
      </xsl:if>
      <xsl:if test="@arg-off">
        <xsl:attribute name="data-arg-off">
          <xsl:value-of select="@arg-off"/>
        </xsl:attribute>
      </xsl:if>
      <p><xsl:value-of select="text()"/></p>
      <xsl:apply-templates select="badge|partialbadge|signal|icon|text|image"/>
    </a>
  </xsl:template>
  <xsl:template match="button[@type]">
    <xsl:if test="@type='momentary'">
      <a href="#" data-actionon="{@action-on}" data-actionoff="{@action-off}" type="button">
        <xsl:if test="(@confirm or @confirmtext)">
          <xsl:attribute name="data-confirm">
            <xsl:choose>
              <xsl:when test="@confirm"><xsl:value-of select="@confirm"/></xsl:when>
              <xsl:otherwise>true</xsl:otherwise>
            </xsl:choose>
          </xsl:attribute>
        </xsl:if>
        <xsl:if test="@confirmtitle">
          <xsl:attribute name="data-confirmtitle">
            <xsl:value-of select="@confirmtitle"/>
          </xsl:attribute>
        </xsl:if>
        <xsl:if test="@confirmtext">
          <xsl:attribute name="data-confirmtext">
            <xsl:value-of select="@confirmtext"/>
          </xsl:attribute>
        </xsl:if>
        <xsl:attribute name="class">
          <xsl:choose>
            <xsl:when test="@class">btn <xsl:value-of select="@class"/></xsl:when>
            <xsl:otherwise>btn btn-default</xsl:otherwise>
          </xsl:choose>
        </xsl:attribute>
        <p><xsl:value-of select="text()"/></p>
        <xsl:apply-templates select="badge|partialbadge|signal|icon|text|image"/>
      </a>
    </xsl:if>
  </xsl:template>
  <!-- button -->
  <!-- buttongroup -->
  <xsl:template match="buttongroup">
    <div role="group">
      <xsl:attribute name="class">
        <xsl:if test="not(@type)">
          <xsl:choose>
            <xsl:when test="@class">btn-group <xsl:value-of select="@class"/></xsl:when>
            <xsl:otherwise>btn-group</xsl:otherwise>
          </xsl:choose>
        </xsl:if>
        <xsl:if test="@type='vertical'">
          <xsl:choose>
            <xsl:when test="@class">btn-group-vertical btn-block <xsl:value-of select="@class"/></xsl:when>
            <xsl:otherwise>btn-group-vertical btn-block</xsl:otherwise>
          </xsl:choose>
        </xsl:if>
        <xsl:if test="@showevent">
          <xsl:text> sect</xsl:text>
        </xsl:if>
      </xsl:attribute>
      <xsl:if test="@showevent">
        <xsl:attribute name="data-showevent">
          <xsl:value-of select="@showevent"/>
        </xsl:attribute>
        <xsl:if test="@showvalue">
          <xsl:attribute name="data-showarg">
            <xsl:value-of select="@showvalue"/>
          </xsl:attribute>
        </xsl:if>
      </xsl:if>
      <xsl:apply-templates select="button|switch|partialswitch"/>
    </div>
  </xsl:template>
  <!-- buttongroup -->
  <!-- icon -->
  <xsl:template match="icon">
    <xsl:choose>
      <xsl:when test="@lib">
        <xsl:if test="@lib='fa'">
          <xsl:choose>
            <xsl:when test="@style">
              <xsl:choose>
                <xsl:when test="@size">
                  <span class="{@style} fa-{@type} fa-{@size}x"></span>
                </xsl:when>
                <xsl:otherwise>
                  <span class="{@style} fa-{@type}"></span>
                </xsl:otherwise>
              </xsl:choose>
            </xsl:when>
            <xsl:otherwise>
              <xsl:choose>
                <xsl:when test="@size">
                  <span class="fas fa-{@type} fa-{@size}x"></span>
                </xsl:when>
                <xsl:otherwise>
                  <span class="fas fa-{@type}"></span>
                </xsl:otherwise>
              </xsl:choose>
            </xsl:otherwise>
          </xsl:choose>
        </xsl:if>
      </xsl:when>
      <xsl:otherwise>
        <span class="glyphicon glyphicon-{@type}"></span>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>
  <!-- icon -->
  <!-- image -->
  <xsl:template match="image">
    <img src="{@source}">
      <xsl:if test="@showevent">
        <xsl:attribute name="data-showevent">
          <xsl:value-of select="@showevent"/>
        </xsl:attribute>
        <xsl:if test="@showvalue">
          <xsl:attribute name="data-showarg">
            <xsl:value-of select="@showvalue"/>
          </xsl:attribute>
        </xsl:if>
      </xsl:if>
      <xsl:variable name="imageclass">
        <xsl:if test="@showevent">sect</xsl:if>
        <xsl:text> img-responsive</xsl:text>
      </xsl:variable>
      <xsl:attribute name="class">
        <xsl:value-of select="normalize-space($imageclass)" />
      </xsl:attribute>
      <xsl:if test="@event">
        <xsl:attribute name="data-event">
          <xsl:value-of select="@event"/>
        </xsl:attribute>
      </xsl:if>
      <xsl:if test="@height|@width">
        <xsl:attribute name="style">
          <xsl:if test="@height">
            <xsl:text>max-height:</xsl:text>
            <xsl:value-of select="@height"/>
            <xsl:text>px;</xsl:text>
          </xsl:if>
          <xsl:if test="@width">
            <xsl:text>max-width:</xsl:text>
            <xsl:value-of select="@width"/>
            <xsl:text>px;</xsl:text>
          </xsl:if>
        </xsl:attribute>
      </xsl:if>
    </img>
  </xsl:template>
  <!-- image -->
  <!-- grid -->
  <xsl:template match="grid">
    <table class="btn-grid">
      <xsl:for-each select="row">
        <tr>
          <xsl:for-each select="cell">
            <td>
              <xsl:apply-templates />
            </td>
          </xsl:for-each>
        </tr>
      </xsl:for-each>
    </table>
  </xsl:template>
  <!-- grid -->
  <!-- switch -->
  <xsl:template match="switch">
    <div role="group">
      <xsl:if test="@event or @action or @join">
        <xsl:choose>
          <xsl:when test="@join">
            <xsl:attribute name="data-event">
              <xsl:value-of select="@join"/>
            </xsl:attribute>
            <xsl:attribute name="data-arg-action">
              <xsl:value-of select="@join"/>
            </xsl:attribute>
          </xsl:when>
          <xsl:otherwise>
            <xsl:if test="@event">
              <xsl:attribute name="data-event">
                <xsl:value-of select="@event"/>
              </xsl:attribute>
            </xsl:if>
            <xsl:if test="@action">
              <xsl:attribute name="data-arg-action">
                <xsl:value-of select="@action"/>
              </xsl:attribute>
            </xsl:if>
          </xsl:otherwise>
        </xsl:choose>
      </xsl:if>
      <xsl:if test="(@confirm or @confirmtext)">
        <xsl:attribute name="data-confirm">
          <xsl:choose>
            <xsl:when test="@confirm"><xsl:value-of select="@confirm"/></xsl:when>
            <xsl:otherwise>true</xsl:otherwise>
          </xsl:choose>
        </xsl:attribute>
      </xsl:if>
      <xsl:if test="@confirmtitle">
        <xsl:attribute name="data-confirmtitle">
          <xsl:value-of select="@confirmtitle"/>
        </xsl:attribute>
      </xsl:if>
      <xsl:if test="@confirmtext">
        <xsl:attribute name="data-confirmtext">
          <xsl:value-of select="@confirmtext"/>
        </xsl:attribute>
      </xsl:if>
      <xsl:attribute name="class">
        <xsl:choose>
          <xsl:when test="@class">btn-group btn-switch <xsl:value-of select="@class"/></xsl:when>
          <xsl:otherwise>btn-group btn-switch</xsl:otherwise>
        </xsl:choose>
        <xsl:if test="@showevent">
          <xsl:text> sect</xsl:text>
        </xsl:if>
      </xsl:attribute>
      <xsl:if test="@showevent">
        <xsl:attribute name="data-showevent">
          <xsl:value-of select="@showevent"/>
        </xsl:attribute>
        <xsl:if test="@showvalue">
          <xsl:attribute name="data-showarg">
            <xsl:value-of select="@showvalue"/>
          </xsl:attribute>
        </xsl:if>
      </xsl:if>
      <xsl:attribute name="data-class-off">
        <xsl:choose>
          <xsl:when test="@class-off"><xsl:value-of select="@class-off"/></xsl:when>
          <xsl:otherwise>btn-danger</xsl:otherwise>
        </xsl:choose>
      </xsl:attribute>
      <xsl:attribute name="data-class-on">
        <xsl:choose>
          <xsl:when test="@class-on"><xsl:value-of select="@class-on"/></xsl:when>
          <xsl:otherwise>btn-success</xsl:otherwise>
        </xsl:choose>
      </xsl:attribute>
      <a href="#" class="btn btn-default" data-arg="false">
        <xsl:choose>
          <xsl:when test="@off"><xsl:value-of select="@off"/></xsl:when>
          <xsl:otherwise>Off</xsl:otherwise>
        </xsl:choose>
      </a>
      <a href="#" class="btn btn-default" data-arg="true">
         <xsl:choose>
          <xsl:when test="@on"><xsl:value-of select="@on"/></xsl:when>
          <xsl:otherwise>On</xsl:otherwise>
        </xsl:choose>
      </a>
    </div>
  </xsl:template>
  <!-- switch -->
  <!-- partialswitch -->
  <xsl:template match="partialswitch">
    <div role="group">
      <xsl:if test="@event or @action or @join">
        <xsl:choose>
          <xsl:when test="@join">
            <xsl:attribute name="data-event">
              <xsl:value-of select="@join"/>
            </xsl:attribute>
            <xsl:attribute name="data-arg-action">
              <xsl:value-of select="@join"/>
            </xsl:attribute>
          </xsl:when>
          <xsl:otherwise>
            <xsl:if test="@event">
              <xsl:attribute name="data-event">
                <xsl:value-of select="@event"/>
              </xsl:attribute>
            </xsl:if>
            <xsl:if test="@action">
              <xsl:attribute name="data-arg-action">
                <xsl:value-of select="@action"/>
              </xsl:attribute>
            </xsl:if>
          </xsl:otherwise>
        </xsl:choose>
      </xsl:if>
      <xsl:if test="(@confirm or @confirmtext)">
        <xsl:attribute name="data-confirm">
          <xsl:choose>
            <xsl:when test="@confirm"><xsl:value-of select="@confirm"/></xsl:when>
            <xsl:otherwise>true</xsl:otherwise>
          </xsl:choose>
        </xsl:attribute>
      </xsl:if>
      <xsl:if test="@confirmtitle">
        <xsl:attribute name="data-confirmtitle">
          <xsl:value-of select="@confirmtitle"/>
        </xsl:attribute>
      </xsl:if>
      <xsl:if test="@confirmtext">
        <xsl:attribute name="data-confirmtext">
          <xsl:value-of select="@confirmtext"/>
        </xsl:attribute>
      </xsl:if>
      <xsl:attribute name="class">
        <xsl:choose>
          <xsl:when test="@class">btn-group btn-pswitch <xsl:value-of select="@class"/></xsl:when>
          <xsl:otherwise>btn-group btn-pswitch</xsl:otherwise>
        </xsl:choose>
        <xsl:if test="@showevent">
          <xsl:text> sect</xsl:text>
        </xsl:if>
      </xsl:attribute>
      <xsl:if test="@showevent">
        <xsl:attribute name="data-showevent">
          <xsl:value-of select="@showevent"/>
        </xsl:attribute>
        <xsl:if test="@showvalue">
          <xsl:attribute name="data-showarg">
            <xsl:value-of select="@showvalue"/>
          </xsl:attribute>
        </xsl:if>
      </xsl:if>
      <xsl:attribute name="data-class-off">
        <xsl:choose>
          <xsl:when test="@class-off"><xsl:value-of select="@class-off"/></xsl:when>
          <xsl:otherwise>btn-danger</xsl:otherwise>
        </xsl:choose>
      </xsl:attribute>
      <xsl:attribute name="data-class-on">
        <xsl:choose>
          <xsl:when test="@class-on"><xsl:value-of select="@class-on"/></xsl:when>
          <xsl:otherwise>btn-success</xsl:otherwise>
        </xsl:choose>
      </xsl:attribute>
      <a href="#" class="btn btn-default" data-arg="Off">
        <xsl:choose>
          <xsl:when test="@off"><xsl:value-of select="@off"/></xsl:when>
          <xsl:otherwise>Off</xsl:otherwise>
        </xsl:choose>
      </a>
      <a href="#" class="btn btn-default" data-arg="On">
         <xsl:choose>
          <xsl:when test="@on"><xsl:value-of select="@on"/></xsl:when>
          <xsl:otherwise>On</xsl:otherwise>
        </xsl:choose>
      </a>
    </div>
  </xsl:template>
  <!-- partialswitch -->
  <!-- pills -->
  <xsl:template match="pills">
    <ul>
      <xsl:if test="@event or @action or @join">
        <xsl:choose>
          <xsl:when test="@join">
            <xsl:attribute name="data-event">
              <xsl:value-of select="@join"/>
            </xsl:attribute>
            <xsl:attribute name="data-arg-action">
              <xsl:value-of select="@join"/>
            </xsl:attribute>
          </xsl:when>
          <xsl:otherwise>
            <xsl:if test="@event">
              <xsl:attribute name="data-event">
                <xsl:value-of select="@event"/>
              </xsl:attribute>
            </xsl:if>
            <xsl:if test="@action">
              <xsl:attribute name="data-arg-action">
                <xsl:value-of select="@action"/>
              </xsl:attribute>
            </xsl:if>
          </xsl:otherwise>
        </xsl:choose>
      </xsl:if>
      <xsl:if test="(@confirm or @confirmtext)">
        <xsl:attribute name="data-confirm">
          <xsl:choose>
            <xsl:when test="@confirm"><xsl:value-of select="@confirm"/></xsl:when>
            <xsl:otherwise>true</xsl:otherwise>
          </xsl:choose>
        </xsl:attribute>
      </xsl:if>
      <xsl:if test="@confirmtitle">
        <xsl:attribute name="data-confirmtitle">
          <xsl:value-of select="@confirmtitle"/>
        </xsl:attribute>
      </xsl:if>
      <xsl:if test="@confirmtext">
        <xsl:attribute name="data-confirmtext">
          <xsl:value-of select="@confirmtext"/>
        </xsl:attribute>
      </xsl:if>
      <xsl:attribute name="class">
        <xsl:text>nav nav-pills nav-stacked</xsl:text>
        <xsl:if test="@showevent">
          <xsl:text> sect</xsl:text>
        </xsl:if>
      </xsl:attribute>
      <xsl:if test="@showevent">
        <xsl:attribute name="data-showevent">
          <xsl:value-of select="@showevent"/>
        </xsl:attribute>
        <xsl:if test="@showvalue">
          <xsl:attribute name="data-showarg">
            <xsl:value-of select="@showvalue"/>
          </xsl:attribute>
        </xsl:if>
      </xsl:if>
      <xsl:for-each select="pill">
        <li>
          <xsl:if test="badge|partialbadge|signal or @showevent">
            <xsl:attribute name="class">
              <xsl:choose>
                <xsl:when test="badge|partialbadge|signal and @showevent">
                  <xsl:text>haschild sect</xsl:text>
                </xsl:when>
                <xsl:when test="badge|partialbadge|signal and not(@showevent)">
                  <xsl:text>haschild</xsl:text>
                </xsl:when>
                <xsl:when test="@showevent and not(badge|partialbadge|signal)">
                  <xsl:text>sect</xsl:text>
                </xsl:when>
              </xsl:choose>
            </xsl:attribute>
          </xsl:if>
          <xsl:if test="@showevent">
            <xsl:attribute name="class">
              <xsl:text>sect</xsl:text>
            </xsl:attribute>
            <xsl:attribute name="data-showevent">
              <xsl:value-of select="@showevent"/>
            </xsl:attribute>
            <xsl:if test="@showvalue">
              <xsl:attribute name="data-showarg">
                <xsl:value-of select="@showvalue"/>
              </xsl:attribute>
            </xsl:if>
          </xsl:if>
          <a href="#" data-arg="{@value}"><xsl:value-of select="text()"/><xsl:apply-templates select="badge|partialbadge|signal"/></a>
        </li>
      </xsl:for-each>
    </ul>
  </xsl:template>
  <!-- pills -->
  <!-- select -->
  <xsl:template match="select">
    <div>
      <xsl:attribute name="class">
        <xsl:text>btn-group btn-select</xsl:text>
        <xsl:if test="@showevent">
          <xsl:text> sect</xsl:text>
        </xsl:if>
        <xsl:if test="@dropup">
          <xsl:text> dropup</xsl:text>
        </xsl:if>
      </xsl:attribute>
      <xsl:if test="@showevent">
        <xsl:attribute name="data-showevent">
          <xsl:value-of select="@showevent"/>
        </xsl:attribute>
        <xsl:if test="@showvalue">
          <xsl:attribute name="data-showarg">
            <xsl:value-of select="@showvalue"/>
          </xsl:attribute>
        </xsl:if>
      </xsl:if>
      <button type="button" class="btn {@class} dropdown-toggle" data-toggle="dropdown" aria-haspopup="true" aria-expanded="false">
        <span>&#160;</span>&#160;<span class="caret"></span>
      </button>
      <ul class="dropdown-menu">
        <xsl:if test="@event or @action or @join">
          <xsl:choose>
            <xsl:when test="@join">
              <xsl:attribute name="data-event">
                <xsl:value-of select="@join"/>
              </xsl:attribute>
              <xsl:attribute name="data-arg-action">
                <xsl:value-of select="@join"/>
              </xsl:attribute>
            </xsl:when>
            <xsl:otherwise>
              <xsl:if test="@event">
                <xsl:attribute name="data-event">
                  <xsl:value-of select="@event"/>
                </xsl:attribute>
              </xsl:if>
              <xsl:if test="@action">
                <xsl:attribute name="data-arg-action">
                  <xsl:value-of select="@action"/>
                </xsl:attribute>
              </xsl:if>
            </xsl:otherwise>
          </xsl:choose>
        </xsl:if>
        <xsl:if test="(@confirm or @confirmtext)">
          <xsl:attribute name="data-confirm">
            <xsl:choose>
              <xsl:when test="@confirm"><xsl:value-of select="@confirm"/></xsl:when>
              <xsl:otherwise>true</xsl:otherwise>
            </xsl:choose>
          </xsl:attribute>
        </xsl:if>
        <xsl:if test="@confirmtitle">
          <xsl:attribute name="data-confirmtitle">
            <xsl:value-of select="@confirmtitle"/>
          </xsl:attribute>
        </xsl:if>
        <xsl:if test="@confirmtext">
          <xsl:attribute name="data-confirmtext">
            <xsl:value-of select="@confirmtext"/>
          </xsl:attribute>
        </xsl:if>
        <xsl:for-each select="item">
          <li>
            <xsl:if test="@showevent">
              <xsl:attribute name="class">
                <xsl:text>sect</xsl:text>
              </xsl:attribute>
              <xsl:attribute name="data-showevent">
                <xsl:value-of select="@showevent"/>
              </xsl:attribute>
              <xsl:if test="@showvalue">
                <xsl:attribute name="data-showarg">
                  <xsl:value-of select="@showvalue"/>
                </xsl:attribute>
              </xsl:if>
            </xsl:if>
            <a href="#" data-arg="{@value}"><xsl:value-of select="text()"/></a>
          </li>
        </xsl:for-each>
      </ul>
    </div>
  </xsl:template>
  <!-- select -->
  <!-- dynamicselect -->
  <xsl:template match="dynamicselect">
    <div class="btn-group btn-select">
      <button type="button" class="btn {@class} dropdown-toggle" data-toggle="dropdown" aria-haspopup="true" aria-expanded="false">
        <span>&#160;</span>&#160;<span class="caret"></span>
      </button>
      <ul class="dropdown-menu dynamic" data-render="{@data}" data-render-template="#dynamicSelect">
        <xsl:if test="@event or @action or @join">
          <xsl:choose>
            <xsl:when test="@join">
              <xsl:attribute name="data-event">
                <xsl:value-of select="@join"/>
              </xsl:attribute>
              <xsl:attribute name="data-arg-action">
                <xsl:value-of select="@join"/>
              </xsl:attribute>
            </xsl:when>
            <xsl:otherwise>
              <xsl:if test="@event">
                <xsl:attribute name="data-event">
                  <xsl:value-of select="@event"/>
                </xsl:attribute>
              </xsl:if>
              <xsl:if test="@action">
                <xsl:attribute name="data-arg-action">
                  <xsl:value-of select="@action"/>
                </xsl:attribute>
              </xsl:if>
            </xsl:otherwise>
          </xsl:choose>
        </xsl:if>
        <xsl:if test="(@confirm or @confirmtext)">
          <xsl:attribute name="data-confirm">
            <xsl:choose>
              <xsl:when test="@confirm"><xsl:value-of select="@confirm"/></xsl:when>
              <xsl:otherwise>true</xsl:otherwise>
            </xsl:choose>
          </xsl:attribute>
        </xsl:if>
        <xsl:if test="@confirmtitle">
          <xsl:attribute name="data-confirmtitle">
            <xsl:value-of select="@confirmtitle"/>
          </xsl:attribute>
        </xsl:if>
        <xsl:if test="@confirmtext">
          <xsl:attribute name="data-confirmtext">
            <xsl:value-of select="@confirmtext"/>
          </xsl:attribute>
        </xsl:if>
      </ul>
    </div>
  </xsl:template>
  <!-- select -->
  <!-- status -->
  <xsl:template match="status">
    <div data-status="{@event}">
      <xsl:if test="@event">
        <xsl:attribute name="data-status">
          <xsl:value-of select="@event"/>
        </xsl:attribute>
      </xsl:if>
      <xsl:attribute name="class">
        <xsl:text>panel panel-default statusgroup clearfix</xsl:text>
        <xsl:if test="@showevent">
          <xsl:text> sect</xsl:text>
        </xsl:if>
      </xsl:attribute>
      <xsl:if test="@showevent">
        <xsl:attribute name="data-showevent">
          <xsl:value-of select="@showevent"/>
        </xsl:attribute>
        <xsl:if test="@showvalue">
          <xsl:attribute name="data-showarg">
            <xsl:value-of select="@showvalue"/>
          </xsl:attribute>
        </xsl:if>
      </xsl:if>
      <xsl:if test="@page">
        <xsl:attribute name="data-nav">
          <xsl:value-of select="translate(@page,translate(@page,$allowedSymbols,''),'')"/>
        </xsl:attribute>
      </xsl:if>
      <div class="panel-body">
        <xsl:apply-templates select="image"/><xsl:apply-templates select="link"/><xsl:apply-templates select="button|swich|partialswitch"/><xsl:apply-templates select="badge|partialbadge|signal"/><strong><xsl:value-of select="text()"/></strong>
        <xsl:if test="@event">
          <br/><span class="status">Unknown</span>
        </xsl:if>
      </div>
      <xsl:if test="@event">
        <xsl:apply-templates select="statussleep"/>
      </xsl:if>
    </div>
  </xsl:template>
  <!-- status -->
  <!-- statussleep -->
  <xsl:template match="statussleep">
    <div class="panel-footer clearfix sect">
      <xsl:if test="@showevent">
        <xsl:attribute name="data-showevent">
          <xsl:value-of select="@showevent"/>
        </xsl:attribute>
        <xsl:if test="@showvalue">
          <xsl:attribute name="data-showarg">
            <xsl:value-of select="@showvalue"/>
          </xsl:attribute>
        </xsl:if>
      </xsl:if>
      <a href="#" data-action="{@action}" class="btn btn-danger" type="button">Sleep</a>
    </div>
  </xsl:template>
  <!-- badge -->
  <xsl:template match="badge">
    <span data-status="{@event}">
      <xsl:attribute name="class">
        <xsl:choose>
          <xsl:when test="@type">
            <xsl:text>label label-default status </xsl:text>
            <xsl:value-of select="@type"/>
          </xsl:when>
          <xsl:otherwise>
            <xsl:text>label label-default status</xsl:text>
          </xsl:otherwise>
        </xsl:choose>
      </xsl:attribute>
      <xsl:value-of select="text()"/>
    </span>
  </xsl:template>
  <!-- badge -->
  <!-- partialbadge -->
  <xsl:template match="partialbadge">
    <span class="label label-default label-pbadge" data-event="{@event}" data-class-off="label-danger" data-class-on="label-success">
    <xsl:attribute name="data-off">
      <xsl:choose>
        <xsl:when test="@off"><xsl:value-of select="@off"/></xsl:when>
        <xsl:otherwise>Off</xsl:otherwise>
      </xsl:choose>
    </xsl:attribute>
    <xsl:attribute name="data-on">
      <xsl:choose>
        <xsl:when test="@on"><xsl:value-of select="@on"/></xsl:when>
        <xsl:otherwise>On</xsl:otherwise>
      </xsl:choose>
    </xsl:attribute>
    <xsl:value-of select="text()"/>
    </span>
  </xsl:template>
  <!-- partialbadge -->
  <!-- link -->
  <xsl:template match="link[@node and not(@url)]">
    <a href="#" data-link-node="{@node}">
      <xsl:attribute name="class">
        <xsl:text>btn btn-outline</xsl:text>
        <xsl:if test="@showevent">
          <xsl:text> sect</xsl:text>
        </xsl:if>
      </xsl:attribute>
      <xsl:if test="@showevent">
        <xsl:attribute name="data-showevent">
          <xsl:value-of select="@showevent"/>
        </xsl:attribute>
        <xsl:if test="@showvalue">
          <xsl:attribute name="data-showarg">
            <xsl:value-of select="@showvalue"/>
          </xsl:attribute>
        </xsl:if>
      </xsl:if>
      <span class="glyphicon glyphicon-new-window"></span><span><xsl:value-of select="text()"/></span>
    </a>
  </xsl:template>
  <xsl:template match="link[@url and not(@node)]">
    <a href="#" data-link-url="{@url}">
      <xsl:attribute name="class">
        <xsl:text>btn btn-outline</xsl:text>
        <xsl:if test="@showevent">
          <xsl:text> sect</xsl:text>
        </xsl:if>
      </xsl:attribute>
      <xsl:if test="@showevent">
        <xsl:attribute name="data-showevent">
          <xsl:value-of select="@showevent"/>
        </xsl:attribute>
        <xsl:if test="@showvalue">
          <xsl:attribute name="data-showarg">
            <xsl:value-of select="@showvalue"/>
          </xsl:attribute>
        </xsl:if>
      </xsl:if>
      <span class="glyphicon glyphicon-new-window"></span><span><xsl:value-of select="text()"/></span>
    </a>
  </xsl:template>
  <xsl:template match="link[not(@url) and not(@node)]">
    <a href="#" data-link-event="{../@event}">
      <xsl:attribute name="class">
        <xsl:text>btn btn-outline</xsl:text>
        <xsl:if test="@showevent">
          <xsl:text> sect</xsl:text>
        </xsl:if>
      </xsl:attribute>
      <xsl:if test="@showevent">
        <xsl:attribute name="data-showevent">
          <xsl:value-of select="@showevent"/>
        </xsl:attribute>
        <xsl:if test="@showvalue">
          <xsl:attribute name="data-showarg">
            <xsl:value-of select="@showvalue"/>
          </xsl:attribute>
        </xsl:if>
      </xsl:if>
      <span class="glyphicon glyphicon-new-window"></span><span><xsl:value-of select="text()"/></span>
    </a>
  </xsl:template>
  <!-- link -->
  <!-- panel -->
  <xsl:template match="panel">
    <div class="panel panel-default">
      <div class="panel-body">
        <div data-event="{@event}" class="panel{@height}px scrollbar-inner"></div>
      </div>
    </div>
    <style>.panel<xsl:value-of select="@height"/>px {height: <xsl:value-of select="@height"/>px; overflow: hidden;}</style>
  </xsl:template>
  <!-- panel -->
  <!-- range -->
  <xsl:template match="range">
    <div>
      <xsl:attribute name="class">
        <xsl:text>range</xsl:text>
        <xsl:if test="@showevent">
          <xsl:text> sect</xsl:text>
        </xsl:if>
      </xsl:attribute>
      <xsl:if test="@showevent">
        <xsl:attribute name="data-showevent">
          <xsl:value-of select="@showevent"/>
        </xsl:attribute>
        <xsl:if test="@showvalue">
          <xsl:attribute name="data-showarg">
            <xsl:value-of select="@showvalue"/>
          </xsl:attribute>
        </xsl:if>
      </xsl:if>
      <xsl:attribute name="data-type">
        <xsl:value-of select="@type"/>
      </xsl:attribute>
      <xsl:if test="@type='vertical'">
        <xsl:attribute name="class">
          <xsl:text>range rangeh</xsl:text>
          <xsl:choose>
            <xsl:when test="@height"><xsl:value-of select="@height"/></xsl:when>
            <xsl:otherwise>200</xsl:otherwise>
          </xsl:choose>
          <xsl:text>px</xsl:text>
        </xsl:attribute>
        <style>.rangeh<xsl:choose>
          <xsl:when test="@height"><xsl:value-of select="@height"/></xsl:when>
            <xsl:otherwise>200</xsl:otherwise>
          </xsl:choose>px {height: <xsl:choose>
            <xsl:when test="@height"><xsl:value-of select="@height"/></xsl:when>
            <xsl:otherwise>200</xsl:otherwise>
          </xsl:choose>px;}</style>
      </xsl:if>
      <div>
        <xsl:if test="@type='vertical'">
          <xsl:attribute name="class">
            <xsl:text>rangew</xsl:text>
            <xsl:choose>
              <xsl:when test="@height"><xsl:value-of select="@height"/></xsl:when>
              <xsl:otherwise>200</xsl:otherwise>
            </xsl:choose>
            <xsl:text>px</xsl:text>
          </xsl:attribute>
          <style>.rangew<xsl:choose>
            <xsl:when test="@height"><xsl:value-of select="@height"/></xsl:when>
              <xsl:otherwise>200</xsl:otherwise>
            </xsl:choose>px {width: <xsl:choose>
              <xsl:when test="@height"><xsl:value-of select="@height"/></xsl:when>
              <xsl:otherwise>200</xsl:otherwise>
            </xsl:choose>px;}</style>
        </xsl:if>
        <form>
          <input data-arg-source="this" data-arg-type="number" type="range" min="{@min}" max="{@max}" step="1">
            <xsl:if test="@event or @action or @join">
              <xsl:choose>
                <xsl:when test="@join">
                  <xsl:attribute name="data-event">
                    <xsl:value-of select="@join"/>
                  </xsl:attribute>
                  <xsl:attribute name="data-action">
                    <xsl:value-of select="@join"/>
                  </xsl:attribute>
                </xsl:when>
                <xsl:otherwise>
                  <xsl:if test="@event">
                    <xsl:attribute name="data-event">
                      <xsl:value-of select="@event"/>
                    </xsl:attribute>
                  </xsl:if>
                  <xsl:if test="@action">
                    <xsl:attribute name="data-action">
                      <xsl:value-of select="@action"/>
                    </xsl:attribute>
                  </xsl:if>
                </xsl:otherwise>
              </xsl:choose>
            </xsl:if>
          </input>
          <output class="toint">
            <xsl:if test="@event or @join">
              <xsl:choose>
                <xsl:when test="@join">
                  <xsl:attribute name="data-event">
                    <xsl:value-of select="@join"/>
                  </xsl:attribute>
                  <xsl:attribute name="data-arg-action">
                    <xsl:value-of select="@join"/>
                  </xsl:attribute>
                </xsl:when>
                <xsl:otherwise>
                  <xsl:if test="@event">
                    <xsl:attribute name="data-event">
                      <xsl:value-of select="@event"/>
                    </xsl:attribute>
                  </xsl:if>
                </xsl:otherwise>
              </xsl:choose>
            </xsl:if>
          </output>
          <xsl:if test="@type='mute'">
            <a href="#" class="btn btn-default" data-arg-on="true" data-arg-off="false">
              <xsl:if test="@event or @action or @join">
                <xsl:choose>
                  <xsl:when test="@join">
                    <xsl:attribute name="data-event">
                      <xsl:value-of select="@join"/>
                      <xsl:text>Muting</xsl:text>
                    </xsl:attribute>
                    <xsl:attribute name="data-action">
                      <xsl:value-of select="@join"/>
                      <xsl:text>Muting</xsl:text>
                    </xsl:attribute>
                  </xsl:when>
                  <xsl:otherwise>
                    <xsl:if test="@event">
                      <xsl:attribute name="data-event">
                        <xsl:value-of select="@event"/>
                        <xsl:text>Muting</xsl:text>
                      </xsl:attribute>
                    </xsl:if>
                    <xsl:if test="@action">
                      <xsl:attribute name="data-action">
                        <xsl:value-of select="@action"/>
                        <xsl:text>Muting</xsl:text>
                      </xsl:attribute>
                    </xsl:if>
                  </xsl:otherwise>
                </xsl:choose>
              </xsl:if>
              <xsl:attribute name="data-class-on">
                <xsl:choose>
                  <xsl:when test="@class-on">btn <xsl:value-of select="@class-on"/></xsl:when>
                  <xsl:otherwise>btn-danger</xsl:otherwise>
                </xsl:choose>
              </xsl:attribute>
              <xsl:text>Mute</xsl:text>
              <xsl:apply-templates select="badge|icon"/>
            </a>
          </xsl:if>
        </form>
      </div>
    </div>
  </xsl:template>
  <!-- range -->
  <!-- field -->
  <xsl:template match="field">
    <div><form><input class="form-control" data-arg-source="this" data-event="{@event}" readonly="true"/></form></div>
  </xsl:template>
  <!-- field -->
  <!-- meter -->
  <xsl:template match="meter">
    <div class="meter" data-event="{@event}">
      <xsl:attribute name="data-type">
        <xsl:value-of select="@type"/>
      </xsl:attribute>
      <xsl:attribute name="data-range">
        <xsl:choose>
          <xsl:when test="@range"><xsl:value-of select="@range"/></xsl:when>
          <xsl:otherwise>perc</xsl:otherwise>
        </xsl:choose>
      </xsl:attribute>
      <div>
        <div data-toggle="tooltip" class="base label-default"></div>
        <div class="bar">
          <div class="label-danger"></div>
          <div class="label-warning"></div>
          <div class="label-success"></div>
        </div>
      </div>
      <p>0</p>
    </div>
  </xsl:template>
  <!-- meter -->
  <!-- signal -->
  <xsl:template match="signal">
    <span data-event="{@event}" class="label signal meter-colour-0">
      <xsl:attribute name="data-range">
        <xsl:choose>
          <xsl:when test="@range"><xsl:value-of select="@range"/></xsl:when>
          <xsl:otherwise>perc</xsl:otherwise>
        </xsl:choose>
      </xsl:attribute>
      <xsl:value-of select="text()"/>
    </span>
  </xsl:template>
  <!-- signal -->
  <!-- gap -->
  <xsl:template match="gap">
    <div>
      <xsl:attribute name="style">
        <xsl:choose>
          <xsl:when test="@value">min-height:<xsl:value-of select="@value"/>px;</xsl:when>
          <xsl:otherwise>min-height:20px;</xsl:otherwise>
        </xsl:choose>
      </xsl:attribute>
    </div>
  </xsl:template>
  <!-- gap -->
  <!-- group -->
  <xsl:template match="group">
    <div>
      <xsl:attribute name="class">
        <xsl:text>well</xsl:text>
        <xsl:if test="@showevent">
          <xsl:text> sect</xsl:text>
        </xsl:if>
      </xsl:attribute>
      <xsl:if test="@showevent">
        <xsl:attribute name="data-showevent">
          <xsl:value-of select="@showevent"/>
        </xsl:attribute>
        <xsl:if test="@showvalue">
          <xsl:attribute name="data-showarg">
            <xsl:value-of select="@showvalue"/>
          </xsl:attribute>
        </xsl:if>
      </xsl:if>
      <xsl:apply-templates/>
    </div>
  </xsl:template>
  <!-- group -->
  <!-- nodel -->
  <xsl:template match="nodel">
    <xsl:if test="@type='description' or @type='actsig' or @type='log' or @type='serverlog' or @type='charts' or @type='console' or @type='params' or @type='remote' or @type='list' or @type='locals' or @type='diagnostics'">
      <div data-nodel="{@type}" class="nodel-{@type}"></div>
    </xsl:if>
    <xsl:if test="@type='add'">
      <div data-nodel="{@type}" class="nodel-{@type}">
        <div class="base">
          <div class="addgrp">
            <div class="dropdown">
              <button class="btn btn-default dropdown-toggle" type="button" data-toggle="dropdown" aria-haspopup="true" aria-expanded="false">
                <xsl:attribute name="id">
                  <xsl:text>addgrp_</xsl:text>
                  <xsl:value-of select="generate-id(.)"/>
                </xsl:attribute>
                <xsl:text>Add node</xsl:text>
              </button>
              <ul class="dropdown-menu">
                <xsl:attribute name="aria-labelledby">
                  <xsl:text>addgrp_</xsl:text>
                  <xsl:value-of select="generate-id(.)"/>
                </xsl:attribute>
                <li>
                  <form>
                    <fieldset>
                      <label>
                        <xsl:attribute name="for">
                          <xsl:text>nodenamval_</xsl:text>
                          <xsl:value-of select="generate-id(.)"/>
                        </xsl:attribute>
                        <xsl:text>Node name</xsl:text>
                      </label>
                      <input class="form-control nodenamval" type="text">
                        <xsl:attribute name="id">
                          <xsl:text>nodenamval_</xsl:text>
                          <xsl:value-of select="generate-id(.)"/>
                        </xsl:attribute>
                      </input>
                      <label>
                        <xsl:attribute name="for">
                          <xsl:text>recipeval_</xsl:text>
                          <xsl:value-of select="generate-id(.)"/>
                        </xsl:attribute>
                        <xsl:text>Recipe</xsl:text>
                      </label>
                      <select class="form-control recipepicker goto" type="text">
                        <xsl:attribute name="id">
                          <xsl:text>recipeval_</xsl:text>
                          <xsl:value-of select="generate-id(.)"/>
                        </xsl:attribute>
                      </select>
                    </fieldset>
                    <div class="btn-toolbar">
                      <button type="submit" class="btn btn-success nodeaddsubmit">Add</button>
                    </div>
                  </form>
                </li>
              </ul>
            </div>
          </div>
        </div>
      </div>
    </xsl:if>
    <xsl:if test="@type='editor'">
      <div data-nodel="{@type}" class="nodel-{@type}">
        <div class="base">
          <div class="panel panel-default">
            <div class="panel-heading accordion-toggle collapsed" data-toggle="collapse" aria-expanded="false">
              <xsl:attribute name="data-target">
                <xsl:text>#editgrp_</xsl:text>
                <xsl:value-of select="generate-id(.)"/>
              </xsl:attribute>
              <div class="panel-title"><h5 class="panel-title">Editor</h5></div>
            </div>
            <div class="panel-collapse collapse" aria-expanded="false">
              <xsl:attribute name="id">
                <xsl:text>editgrp_</xsl:text>
                <xsl:value-of select="generate-id(.)"/>
              </xsl:attribute>
              <div class="panel-body">           
                <div class="row">
                  <div class="col-sm-12">
                    <div class="flex">
                      <div class="flexgrow">
                        <select class="picker form-control"></select>
                      </div>
                      <div>
                        <button class="btn btn-danger script_delete" disabled="disabled">Delete</button><xsl:text>&#8196;</xsl:text>
                        <div class="addgrp">
                          <div class="dropdown">
                            <button class="btn btn-default" type="button" data-toggle="dropdown" aria-haspopup="true" aria-expanded="false">
                              <xsl:attribute name="id">
                                <xsl:text>addgrp_</xsl:text>
                                <xsl:value-of select="generate-id(.)"/>
                              </xsl:attribute>
                              <xsl:text>Add</xsl:text>
                            </button>
                            <ul class="dropdown-menu" aria-labelledby="addgrp_{{:~gid}}">
                              <li>
                                <form>
                                  <fieldset>
                                    <label>
                                      <xsl:attribute name="for">
                                        <xsl:text>scriptnameval_</xsl:text>
                                        <xsl:value-of select="generate-id(.)"/>
                                      </xsl:attribute>
                                      <xsl:text>File name</xsl:text>
                                    </label>
                                    <input class="form-control scriptnamval" type="text">
                                      <xsl:attribute name="id">
                                        <xsl:text>scriptnameval_</xsl:text>
                                        <xsl:value-of select="generate-id(.)"/>
                                      </xsl:attribute>
                                    </input>
                                  </fieldset>
                                  <button type="submit" class="btn btn-success scriptsubmit">Add</button>
                                </form>
                              </li>
                            </ul>
                          </div>
                        </div>
                        <button class="btn btn-default script_default">Edit script.py</button><xsl:text>&#8196;</xsl:text>
                        <button class="btn btn-success script_save" disabled="disabled">Save</button>
                      </div>
                    </div>
                  </div>
                </div>
                <div class="row">
                  <div class="col-sm-12">
                    <div class="editor">
                      <textarea></textarea>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </xsl:if>
    <xsl:if test="@type='toolkit'">
      <div data-nodel="{@type}" class="nodel-{@type}">
        <div class="base">
          <div class="row">
            <div class="col-sm-12">
              <div class="toolkit">
                <textarea></textarea>
              </div>
            </div>
          </div>
        </div>
      </div>
    </xsl:if>
  </xsl:template>
  <!-- nodel -->
</xsl:stylesheet>