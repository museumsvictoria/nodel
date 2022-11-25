<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">
  <xsl:import href="templates.xsl"/>
  <xsl:output method="html" indent="yes" doctype-system="about:legacy-compat"/>
  <xsl:variable name="allowedSymbols" select="'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789'"/>
  <xsl:template match="/">
    <html lang="en" xsl:version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
      <head>
        <meta charset="utf-8"/>
        <meta http-equiv="X-UA-Compatible" content="IE=edge"/>
        <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no"/>
        <meta name="apple-mobile-web-app-capable" content="yes"/>
        <meta name="apple-mobile-web-app-status-bar-style" content="black"/>
        <meta name="theme-color" content="#000000"/>
        <!-- The above 3 meta tags *must* come first in the head; any other head content must come *after* these tags -->
        <title></title>
        <!-- Bootstrap -->
        <link rel="stylesheet">
          <xsl:attribute name="href">
            <xsl:choose>
              <xsl:when test="/pages/@theme">
                <xsl:text>v1/css/components.</xsl:text>
                <xsl:value-of select="/pages/@theme"/>
                <xsl:text>.css</xsl:text>
              </xsl:when>
              <xsl:otherwise>
                <xsl:text>v1/css/components.css</xsl:text>
              </xsl:otherwise>
            </xsl:choose>
          </xsl:attribute>
        </link>
        <xsl:if test="not(/pages/@core)">
          <xsl:if test="/pages/@css">
            <link rel="stylesheet">
              <xsl:attribute name="href">
                <xsl:value-of select="/pages/@css"/>
              </xsl:attribute>
            </link>
          </xsl:if>
        </xsl:if>
        <link href="v1/img/favicon.ico" rel="shortcut icon"/>
        <link href="v1/img/apple-touch-icon.png" rel="apple-touch-icon"/>
      </head>
      <body>
        <xsl:if test="//footer or /pages/@core">
          <xsl:variable name="bodyclass">
            <xsl:if test="//footer">hasfooter</xsl:if>
            <xsl:if test="/pages/@core"> core</xsl:if>
          </xsl:variable>
          <xsl:attribute name="class">
            <xsl:value-of select="normalize-space($bodyclass)" />
          </xsl:attribute>
        </xsl:if>
        <!-- main nav -->
        <nav class="" data-toggle="collapse" data-target=".nav-collapse">
          <xsl:attribute name="class">
            <xsl:choose>
              <xsl:when test="/pages/@theme">
                <xsl:text>navbar navbar-fixed-top navbar-</xsl:text>
                <xsl:value-of select="/pages/@theme"/>
              </xsl:when>
              <xsl:otherwise>
                <xsl:text>navbar navbar-fixed-top navbar-inverse</xsl:text>
              </xsl:otherwise>
            </xsl:choose>
          </xsl:attribute>
          <div class="container-fluid">
            <!-- Brand and toggle get grouped for better mobile display -->
            <div class="navbar-header">
              <button type="button" class="navbar-toggle collapsed" data-toggle="collapse" data-target="#nodel-navbar" aria-expanded="false">
                <span class="sr-only">Toggle navigation</span>
                <span class="icon-bar"></span>
                <span class="icon-bar"></span>
                <span class="icon-bar"></span>
              </button>
              <div class="navbar-brand">
                <a>
                  <xsl:if test="/pages/header/@destination">
                    <xsl:attribute name="href">
                      <xsl:value-of select="/pages/header/@destination"/>
                    </xsl:attribute>
                  </xsl:if>
                  <xsl:choose>
                    <xsl:when test="/pages/@logo">
                      <img src="{/pages/@logo}"/>
                    </xsl:when>
                    <xsl:otherwise>
                      <img src="v1/img/logo.png"/>
                    </xsl:otherwise>
                  </xsl:choose>
                </a>
                <xsl:if test="/pages/header/nodel/@type='hosticon'">
                  <span class="nodel-icon"><a><img src="data:image/gif;base64,R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7"/></a></span>
                </xsl:if>
                <span id="title"><xsl:value-of select="/pages/@title"/></span>
              </div>
            </div>
            <!-- Collect the nav links, forms, and other content for toggling -->
            <div class="collapse navbar-collapse" id="nodel-navbar" role="navigation">
              <ul class="nav navbar-nav">
                <xsl:for-each select="/pages/page|/pages/pagegroup">
                  <xsl:if test="self::pagegroup">
                  <li class="dropdown">
                    <a class="dropdown-toggle" data-toggle="dropdown" role="button" aria-haspopup="true" aria-expanded="false"><xsl:value-of select="@title"/><span class="caret"></span></a>
                    <ul class="dropdown-menu">
                      <xsl:for-each select="page">
                        <li>
                          <a role="button" data-nav="{translate(@title,translate(@title,$allowedSymbols,''),'')}" data-toggle="collapse" data-target="#nodel-navbar.in">
                            <xsl:if test="@action">
                              <xsl:attribute name="data-action">
                                <xsl:value-of select="@action"/>
                              </xsl:attribute>
                            </xsl:if>
                            <xsl:value-of select="@title"/>
                          </a>
                        </li>
                      </xsl:for-each>
                    </ul>
                  </li>
                  </xsl:if>
                    <xsl:if test="self::page">
                    <li>
                      <a role="button" data-nav="{translate(@title,translate(@title,$allowedSymbols,''),'')}" data-toggle="collapse" data-target="#nodel-navbar.in">
                        <xsl:if test="@action">
                          <xsl:attribute name="data-action">
                            <xsl:value-of select="@action"/>
                          </xsl:attribute>
                        </xsl:if>
                        <xsl:value-of select="@title"/>
                      </a>
                    </li>
                  </xsl:if>
                </xsl:for-each>
              <!--<xsl:for-each select="/pages/page[not(page)]">
                <li><a href="#" data-nav="{@title}"><xsl:value-of select="@title"/></a></li>
              </xsl:for-each>-->
              </ul>
              <div class="navbar-right">
                <xsl:if test="/pages/header/input or /pages/header/button or /pages/header/switch">
                  <div class="navbar-form">
                    <xsl:for-each select="/pages/header/input|/pages/header/button|/pages/header/switch">
                      <xsl:choose>
                        <xsl:when test="@type='checkbox'">
                          <div class="checkbox">
                            <label>
                              <input type="checkbox" data-action="{@action}" data-event="{@event}" value="true"/><xsl:value-of select="text()"/>
                            </label>
                          </div>
                        </xsl:when>
                        <xsl:otherwise>
                          <xsl:apply-templates select="."/>
                        </xsl:otherwise>
                      </xsl:choose>
                    </xsl:for-each>
                  </div>
                </xsl:if>
                <xsl:if test="/pages/header/nodel">
                  <xsl:for-each select="/pages/header/nodel">
                    <xsl:if test="@type='edit'">
                      <ul class="nav navbar-nav edtgrp">
                        <li class="dropdown">
                          <a class="dropdown-toggle" data-toggle="dropdown" role="button" aria-haspopup="true" aria-expanded="false">Functions <span class="caret"></span></a>
                          <ul class="dropdown-menu">
                            <li class="form">
                              <div>
                                <input class="form-control renamenode" type="text"/>
                                <button class="btn btn-default renamenodesubmit">Rename</button>
                              </div>
                            </li>
                            <li class="form">
                              <div class="checkbox">
                                <label>
                                  <input type="checkbox" class="advancedmode"/>Override signals
                                </label>
                              </div>
                            </li>
                            <li class="form">
                              <div>
                                <div class="btn-group btn-group-justified">
                                  <a class="btn btn-danger deletenodesubmit" role="button">Delete node</a>
                                  <a class="btn btn-warning restartnodesubmit" role="button">Restart node</a>
                                </div>
                              </div>
                            </li>
                          </ul>
                        </li>
                      </ul>
                    </xsl:if>
                    <xsl:if test="@type='nav'">
                      <ul class="nav navbar-nav srchgrp">
                        <li class="dropdown">
                          <a href="#" class="dropdown-toggle" data-toggle="dropdown" role="button" aria-haspopup="true" aria-expanded="false">Nav <span class="caret"></span></a>
                          <ul class="dropdown-menu">
                            <li class="form">
                              <div>
                                <input class="form-control node goto" type="text" placeholder="search nodes"/>
                              </div>
                            </li>
                            <li class="form">
                              <div>
                                <div class="btn-group btn-group-justified uipicker">
                                  <div class="btn-group" role="group">
                                    <button type="button" class="btn btn-default dropdown-toggle" data-toggle="dropdown" aria-haspopup="true" aria-expanded="false" disabled="disabled">
                                      Select UI <span class="caret"></span>
                                    </button>
                                    <ul class="dropdown-menu">
                                    </ul>
                                  </div>
                                </div>
                              </div>
                            </li>
                            <li role="separator" class="divider"></li>
                            <li><a href="/toolkit.xml">Toolkit</a></li>
                            <li><a href="/diagnostics.xml">Diagnostics</a></li>
                          </ul>
                        </li>
                      </ul>
                    </xsl:if>
                  </xsl:for-each>
                </xsl:if>
                <p class="navbar-text" id="clock"></p>
              </div>
            </div><!-- /.navbar-collapse -->
          </div><!-- /.container-fluid -->
        </nav>
        <!-- end main nav -->
        <!-- offline modal -->
        <div class="modal" id="offline" tabindex="-1" role="dialog" aria-labelledby="offlinelabel" data-backdrop="static" data-keyboard="false" aria-hidden="true">
          <div class="modal-dialog">
            <div class="modal-content">
              <div class="modal-header">
                <h4 class="modal-title" id="offlinelabel">Offline</h4>
              </div>
              <div class="modal-body">
                <p>The system is currently offline. Please wait...</p>
              </div>
            </div>
          </div>
        </div>
        <!-- end offline modal -->
        <!-- confirm modal -->
        <div class="modal" id="confirm" tabindex="-1" role="dialog" aria-labelledby="confirmlabel" aria-hidden="true">
          <div class="modal-dialog">
            <div class="modal-content">
              <div class="modal-header">
                <button type="button" class="close" data-dismiss="modal">&#215;</button>
                <h4 class="modal-title" id="confirmlabel"></h4>
              </div>
              <div class="modal-body">
                <p id="confirmtext"></p>
                <div id="confirmkeypad">
                  <div class="row">
                    <div class="col-xs-4"><a href="#" class="btn btn-block btn-default" data-keypad="1">1</a></div>
                    <div class="col-xs-4"><a href="#" class="btn btn-block btn-default" data-keypad="2">2</a></div>
                    <div class="col-xs-4"><a href="#" class="btn btn-block btn-default" data-keypad="3">3</a></div>
                  </div>
                  <div class="row">
                    <div class="col-xs-4"><a href="#" class="btn btn-block btn-default" data-keypad="4">4</a></div>
                    <div class="col-xs-4"><a href="#" class="btn btn-block btn-default" data-keypad="5">5</a></div>
                    <div class="col-xs-4"><a href="#" class="btn btn-block btn-default" data-keypad="6">6</a></div>
                  </div>
                  <div class="row">
                    <div class="col-xs-4"><a href="#" class="btn btn-block btn-default" data-keypad="7">7</a></div>
                    <div class="col-xs-4"><a href="#" class="btn btn-block btn-default" data-keypad="8">8</a></div>
                    <div class="col-xs-4"><a href="#" class="btn btn-block btn-default" data-keypad="9">9</a></div>
                  </div>
                  <div class="row">
                    <div class="col-xs-4 col-xs-offset-4"><a href="#" class="btn btn-block btn-default" data-keypad="0">0</a></div>
                    <div class="col-xs-4"><a href="#" class="btn btn-block btn-default" data-keypad="-1">&#x232b;</a></div>
                  </div>
                  <div class="row">
                    <div class="col-xs-12">
                      <input id="confirmcodesrc" type="hidden" data-event="ConfirmCode"/>
                      <input id="confirmcode" class="form-control" type="password" readonly="true"/>
                    </div>
                  </div>
                </div>
              </div>
              <div class="modal-footer">
                <button type="button" class="btn btn-default" data-dismiss="modal">Cancel</button>
                <button id="confirmaction" class="btn btn-danger btn-ok">Ok</button>
              </div>
            </div>
          </div>
        </div>
        <!-- end offline modal -->
        <!-- alert -->
        <div class="alert collapse alert-floating alert-info">
          <div class="message"></div>
        </div>
        <!-- end alert -->
        <!-- pages -->
        <xsl:for-each select="//page">
          <div class="container-fluid page" data-section="{translate(@title,translate(@title,$allowedSymbols,''),'')}">
            <xsl:apply-templates select="row"/>
            <xsl:apply-templates select="*[starts-with(name(), 'special_')]"/>
          </div>
        </xsl:for-each>
        <!-- end pages -->
        <!-- footer -->
        <xsl:if test="//footer">
          <footer>
            <xsl:attribute name="class">
              <xsl:choose>
                <xsl:when test="/pages/@theme">
                  <xsl:text>navbar navbar-fixed-bottom navbar-</xsl:text>
                  <xsl:value-of select="/pages/@theme"/>
                </xsl:when>
                <xsl:otherwise>
                  <xsl:text>navbar navbar-fixed-bottom navbar-inverse</xsl:text>
                </xsl:otherwise>
              </xsl:choose>
            </xsl:attribute>
            <div class="container-fluid">
              <xsl:for-each select="//footer">
                <xsl:apply-templates select="row"/>
              </xsl:for-each>
            </div>
          </footer>
        </xsl:if>
        <!-- end footer -->
        <script src="v1/js/components.min.js"></script>
        <script src="v1/js/nodel.js"></script>
        <xsl:if test="not(/pages/@core)">
          <xsl:if test="/pages/@js">
            <script>
              <xsl:attribute name="src">
                <xsl:value-of select="/pages/@js"/>
              </xsl:attribute>
            </script>
          </xsl:if>
        </xsl:if>
        <script id="dynamicSelect" type="text/x-jsrender">
        <![CDATA[
          {{for arg}}
            <li><a href="#" data-arg="{{if key}}{{>key}}{{else}}{{>value}}{{/if}}">{{>value}}</a></li>
          {{/for}}
        ]]>
        </script>
        <script id="dynamicButtonGroup" type="text/x-jsrender">
          <![CDATA[
          {{for arg}}
            <a href="#" class="btn btn-default btn-of-groups" data-arg="{{if key}}{{>key}}{{else}}{{>value}}{{/if}}">{{>value}}</a>
          {{/for}}
        ]]>
        </script>
        <script id="switchTmpl" type="text/x-jsrender">
        <![CDATA[
          <%if type=="string"%>
            <%include ~id=~genid() tmpl="#stringTmpl"/%>
          <%else type=="boolean"%>
            <%include ~id=~genid() tmpl="#booleanTmpl"/%>
          <%else type=="number" || type=="integer"%>
            <%include ~id=~genid() tmpl="#numberTmpl"/%>
          <%else type=="object"%>
            <%include ~id=~genid() tmpl="#objectTmpl"/%>
          <%else type=="array"%>
            <%include ~id=~genid() tmpl="#arrayTmpl"/%>
          <%/if%>
        ]]>
        </script>
        <script id="baseTmpl" type="text/x-jsrender">
        <![CDATA[
          <form data-form="true" class="base" autocomplete="off">
            <%if disabled%>
            {^{if true ~initHid('_$grpeditable')}}
              <fieldset disabled data-link="disabled{:!_$grpeditable}">
            <%else%>
              <fieldset>
            <%/if%>
              <%if isgrouped%>
              {^{if true ~initHid('_$grpvisible')}}
                {^{if _$grpvisible}}
              <%/if%>
              <%if btntop%>
                <button type="submit" class="btn <%if btncolour%>btn-<%:btncolour%><%else%>btn-default<%/if%> btn-top<%if disabled%> disabled<%/if%>" title="<%:btntitle%>">
                  <%>btntitle%>
                  <%if btnfaicon%>
                    <span class="glyphicon glyphicon-<%:btnicon%>" aria-hidden="true"></span>
                  <%/if%>
                  <%if btnfaicon%>
                    <span class="fa fa-<%:btnfaicon%>" aria-hidden="true"></span>
                  <%/if%>
                </button>
              <%/if%>
              <%if title%>
                <h6 class="text-break"><%>title%></h6>
              <%/if%>
              <%for schema ~key=key?key:'' ~nokeytitle=nokeytitle%>
                <%if type=="object"%>
                  {^{if ~initObj('<%:~key%>', <%:~key%>)}}
                    {^{for <%:~key%>}}
                      <%props properties sort="prop.order"%>
                        <%for prop ~key=key%>
                          <%include tmpl="#switchTmpl"/%>
                        <%/for%>
                      <%/props%>
                    {{/for}}
                  {{/if}}
                <%else%>
                  <%include tmpl="#switchTmpl"/%>
                <%/if%>
              <%/for%>
              <%if !btntop%>
                <button type="submit" class="btn <%if btncolour%>btn-<%:btncolour%><%else%>btn-default<%/if%>" title="<%:btntitle%>">
                  <%>btntext%>
                  <%if btnicon%>
                    <span class="glyphicon glyphicon-<%:btnicon%>" aria-hidden="true"></span>
                  <%/if%>
                  <%if btnfaicon%>
                    <span class="fa fa-<%:btnfaicon%>" aria-hidden="true"></span>
                  <%/if%>
                </button>
              <%/if%>
              <%if isgrouped%>
                {{/if}}
              {{/if}}
              <%/if%>
            <%if disabled%>
                </fieldset>
              {{/if}}
            <%else%>
              </fieldset>
            <%/if%>
          </form>
        ]]>
        </script>
        <script id="objectTmpl" type="text/x-jsrender">
        <![CDATA[
          <div class="panel panel-default">
            <div class="panel-heading accordion-toggle collapsed" data-toggle="collapse" data-link="data-target{:'#'+~idxid(~idx,'<%:~id%>_array_<%:~key%>')}" aria-expanded="false">
              <%if title%>
                <div class="panel-title"><h5 class="panel-title"><%>title%></h5></div>
              <%else ~inobj || !~nokeytitle%>
                <div class="panel-title"><h5 class="panel-title"><%>~key%></h5></div>
              <%else%>
                <div class="panel-title"><h5 class="panel-title">&nbsp;</h5></div>
              <%/if%>
            </div>
            <div data-link="id{:~idxid(~idx,'<%:~id%>_array_<%:~key%>')}" class="panel-collapse collapse" aria-expanded="false">
              <div class="panel-body">
              {^{if true ~initHid('_$visible')}}
                {^{if _$visible}}
                  {^{if ~initObj('<%:~key%>', <%:~key%>)}}
                    {^{for <%:~key%>}}
                      <%props properties sort="prop.order"%>
                        <%for prop ~key=key%>
                          <%include tmpl="#switchTmpl" ~inobj=true/%>
                        <%/for%>
                      <%/props%>
                    {{/for}}
                  {{/if}}
                {{/if}}
              {{/if}}
              </div>
            </div>
          </div>
        ]]>
        </script>
        <script id="arrayTmpl" type="text/x-jsrender">
        <![CDATA[
          <div class="panel panel-default">
            <div class="panel-heading accordion-toggle collapsed" data-toggle="collapse" data-link="data-target{:'#'+~idxid(~idx,'<%:~id%>_array_<%:~key%>')}" aria-expanded="false">
              <%if title%>
                <div class="panel-title"><h5 class="panel-title"><%>title%></h5></div>
              <%else ~inobj || !~nokeytitle%>
                <div class="panel-title"><h5 class="panel-title"><%>~key%></h5></div>
              <%else%>
                <div class="panel-title"><h5 class="panel-title">&nbsp;</h5></div>
              <%/if%>
            </div>
            <div data-link="id{:~idxid(~idx,'<%:~id%>_array_<%:~key%>')}" class="panel-collapse collapse" aria-expanded="false">
              <table class="table">
                <tbody>
                  {^{if true ~initHid('_$visible')}}
                    {^{if _$visible}}
                      {^{if ~initArr('<%:~key%>', <%:~key%>)}}
                        {^{for <%:~key%>}}
                          <%for items%>
                            <%if type=="object"%>
                              {^{if true ~idx=~idx?~idx+'_'+#getIndex():#getIndex()}}
                                <tr>
                                  <td class="col-sm-12">
                                    <%props properties sort="prop.order"%>
                                      <%for prop ~key=key%>
                                        <%include tmpl="#switchTmpl" ~inobj=true/%>
                                      <%/for%>
                                    <%/props%>
                                    <div class="btn-group btn-group-xs">
                                      <button type="button" class="btn btn-default up" data-link="class{:#getIndex() <= 0?'btn btn-default up disabled':'btn btn-default up'}"><span class="glyphicon glyphicon-chevron-up" aria-hidden="true"></span></button>
                                      <button type="button" class="btn btn-default del"><span class="fa fa-trash text-danger" aria-hidden="true"></span></button>
                                      <button type="button" class="btn btn-default down" data-link="class{:#getIndex() >= #parent.data.length-1?'btn btn-default down disabled':'btn btn-default down'}"><span class="glyphicon glyphicon-chevron-down" aria-hidden="true"></span></button>
                                    </div>
                                  </td>
                                </tr>
                              {{/if}}
                            <%/if%>
                          <%/for%>
                        {{/for}}
                        <tr>
                          <td><button type="button" data-for="<%:~key%>" class="btn btn-default btn-sm text-primary add"><span class="glyphicon glyphicon-plus" aria-hidden="true"></span></button></td>
                        </tr>
                      {{/if}}
                    {{/if}}
                  {{/if}}
                </tbody>
              </table>
            </div>
          </div>
        ]]>
        </script>
        <script id="stringTmpl" type="text/x-jsrender">
        <![CDATA[
          <div class="form-group">
            <%if title%>
              <label data-link="for{:~idxid(~idx,'<%:~id%>_field_<%:~key%>')}"><%>title%></label>
            <%else ~inobj || !~nokeytitle%>
              <label data-link="for{:~idxid(~idx,'<%:~id%>_field_<%:~key%>')}"><%>~key%></label>
            <%/if%>
            <%if enum%>
              <select title="<%>desc%>" class="form-control" placeholder="<%>hint%>" data-link="{:<%:~key%>:} id{:~idxid(~idx,'<%:~id%>_field_<%:~key%>')}">
                <option value=""></option>
                <%for enum%>
                  <option value="<%:#data%>"><%>#data%></option>
                <%/for%>
              </select>
            <%else%>
              <%if format == 'date' || format == 'time' || format == 'password' || format == 'color'%>
                <input title="<%>desc%>" type="<%>format%>" class="form-control" placeholder="<%>hint%>" data-link="{:<%:~key%>:} id{:~idxid(~idx,'<%:~id%>_field_<%:~key%>')}"/>
              <%else format == 'long'%>
                <textarea title="<%>desc%>" class="form-control" placeholder="<%>hint%>" data-link="{:<%:~key%>:} id{:~idxid(~idx,'<%:~id%>_field_<%:~key%>')}"></textarea>
              <%else%>
                <input title="<%>desc%>" type="text" class="form-control" placeholder="<%>hint%>" data-link="{:<%:~key%>:} id{:~idxid(~idx,'<%:~id%>_field_<%:~key%>')}"/>
              <%/if%>
            <%/if%>
          </div>
        ]]>
        </script>
        <script id="numberTmpl" type="text/x-jsrender">
        <![CDATA[
          <div class="form-group">
            <%if title%>
              <label data-link="for{:~idxid(~idx,'<%:~id%>_field_<%:~key%>')}"><%>title%></label>
            <%else ~inobj || !~nokeytitle%>
              <label data-link="for{:~idxid(~idx,'<%:~id%>_field_<%:~key%>')}"><%>~key%></label>
            <%/if%>
            <input title="<%>desc%>" type="<%if format=='range'%>range<%else%>number<%/if%>" class="form-control" placeholder="<%>hint%>" step="<%if step%><%>step%><%else%><%if type=='integer'%>1<%else%>any<%/if%><%/if%>" <%if min%>min="<%>min%>"<%/if%> <%if max%>max="<%>max%>"<%/if%> data-link="{intToStr:<%:~key%>:strToInt} id{:~idxid(~idx,'<%:~id%>_field_<%:~key%>')}"/>
            <%if format=='range'%><output data-link="{intToStr:<%:~key%>:strToInt}"></output><%/if%>
          </div>
        ]]>
        </script>
        <script id="booleanTmpl" type="text/x-jsrender">
        <![CDATA[
          <div class="form-group">
            <div class="checkbox" data-link="id{:~idxid(~idx,'<%:~id%>_field_<%:~key%>_group')}">
              <label data-link="for{:~idxid(~idx,'<%:~id%>_field_<%:~key%>')}">
                <input title="<%>desc%>" type="checkbox" class="styled" data-link="{:<%:~key%>:} id{:~idxid(~idx,'<%:~id%>_field_<%:~key%>')}"/>
                <%if title%>
                  <%>title%>
                <%else ~inobj || !~nokeytitle%>
                  <%>~key%>
                <%/if%>
              </label>
            </div>
          </div>
        ]]>
        </script>
        <script id="remoteTmpl" type="text/x-jsrender">
        <![CDATA[
          <form data-form="true" class="base" autocomplete="off">
            {^{if true ~initHid('_$visible') ~initHid('_$filtername') ~initHid('_$filldown')}}
            <fieldset>
              <%for schema ~key=key?key:''%>
                <%if type=="object"%>
                  <%props properties%>
                    <%if true ~id=~genid() ~grouptitle=(key=='actions')?'Actions':'Events' ~fieldtitle=(key=='actions')?'Action':'Event' ~fieldkey=(key=='actions')?'action':'event'%>
                      <div class="panel panel-default">
                        <div class="panel-heading accordion-toggle collapsed" data-toggle="collapse" data-target="#<%:~id%>_remote_group">
                          <h5 class="panel-title"><%>~grouptitle%></h5>
                        </div>
                        <div id="<%:~id%>_remote_group" class="panel-collapse collapse">
                          <div class="panel-body">
                            {^{if ~root._$visible}}
                            <table class="tableremote">
                              <thead>
                                <tr>
                                  <td>
                                    <div class="">
                                      <input class="form-control" type="text" data-link="_$filtername" placeholder="filter"/>
                                    </div>
                                  </td>
                                  <td></td>
                                  <td>
                                    <div class="input-group">
                                      <input class="form-control node" type="text" data-link="_$filldown" placeholder="fill selected"/>
                                      <span class="input-group-btn">
                                        <button type="button" class="btn btn-default remotenodecopy <%:~fieldkey%>"><i class="far fa-copy"></i></button>
                                      </span>
                                    </div>
                                  </td>
                                  <td><button type="button" class="btn btn-default remotefill <%:~fieldkey%>"><i class="fas fa-magic"></i></button></td>
                                </tr>
                                <tr>
                                  <th><label><input type="checkbox" class="remoteselectall <%:~fieldkey%>"/> <b>Name</b></label></th>
                                  <th></th>
                                  <th>Node</th>
                                  <th><%>~fieldtitle%></th>
                                </tr>
                              </thead>
                              <tbody>
                                <%for prop ~key=key%>
                                  {^{if ~initObj(<%:~key%>, <%:~key%>)}}
                                    {^{for <%:~key%>}}
                                      <%props properties%>
                                        {^{if ~iswithin('<%:key%>',~root._$filtername)}}
                                          <tr>
                                            <%for prop ~key=key%>
                                              {^{if ~initObj('<%:~key%>', <%:~key%>)}}
                                                {^{for <%:~key%>}}
                                                  <td>
                                                    <label class="multi">
                                                      {^{if ~initHid('_$checked')}}
                                                        <input type="checkbox" data-link="_$checked"/>
                                                      {{/if}}
                                                      <%>title%>
                                                    </label>
                                                  </td>
                                                  <td>
                                                    {^{if ~initHid('_$status')}}
                                                      {^{if _$status == 'Wired'}}
                                                        <a data-link="href{:'/nodes.xml?filter='+node}"><span class="binding wired fas fa-link reachable"></span></a>
                                                      {{else}}
                                                        <a><span class="binding fas fa-unlink"></span></a>
                                                      {{/if}}
                                                    {{/if}}
                                                  </td>
                                                  <td>
                                                    <div>
                                                      <input spellcheck="false" placeholder="node" type="text" class="form-control node" data-link="node"/>
                                                    </div>
                                                  </td>
                                                  <td>
                                                    <div>
                                                      <input spellcheck="false" placeholder="<%>~fieldkey%>" type="text" class="form-control <%>~fieldkey%>" data-link="<%>~fieldkey%>"/>
                                                    </div>
                                                  </td>
                                                {{/for}}
                                              {{/if}}
                                            <%/for%>
                                          </tr>
                                        {{/if}}
                                      <%/props%>
                                    {{/for}}
                                  {{/if}}
                                <%/for%>
                              </tbody>
                            </table>
                            {{/if}}
                          </div>
                        </div>
                      </div>
                    <%/if%>
                  <%/props%>
                <%/if%>  
              <%/for%>
              <button type="submit" class="btn btn-success" title="Remote">Save</button>
            </fieldset>
            {{/if}}
          </form>
        ]]>
        </script>
        <script id="actsigHoldingTmpl" type="text/x-jsrender">
        <![CDATA[
          <div class="row">
            <div class="col-sm-12">
              <button type="submit" class="btn btn-warning enable" title="Enable">Enable</button>
              <div class="loader"></div>
            </div>
          </div>
        ]]>
        </script>
        <script id="actsigTmpl" type="text/x-jsrender">
        <![CDATA[
          {{for forms}}
            {{include tmpl="#actsigTmplItem" ~len=#parent.data.length/}}
          {{/for}}
          {{props groups}}
            {{if true ~id=~genid()}}
              <div class="panel panel-default isgrouped">
                <div class="panel-heading accordion-toggle collapsed" data-toggle="collapse" data-target="#{{:~id}}_actsig_group">
                  <h5 class="panel-title">{{>key}}</h5>
                </div>
                <div id="{{:~id}}_actsig_group" class="panel-collapse collapse">
                  <div class="panel-body">
                    {{for prop}}
                      {{include tmpl="#actsigTmplItem" ~len=#parent.data.length ~isgrouped=true/}}
                    {{/for}}
                  </div>
                </div>
              </div>
            {{/if}}
          {{/props}}
        ]]>
        </script>
        <script id="actsigTmplItem" type="text/x-jsrender">
        <![CDATA[
          <div class="row">
            {{if action}}
              <div class="col-sm-6">
                <div {{props action}}
                  data-{{:key}}='{{:prop}}'
                {{/props}} {{if ~isgrouped}}data-isgrouped="true"{{/if}} data-btnfaicon="running">
                </div>
              </div>
            {{/if}}
            {{if event}}
              {{if action}}
              <div class="col-sm-6">
              {{else}}
              <div class="col-sm-offset-6 col-sm-6">
              {{/if}}
                <div {{props event}}
                  data-{{:key}}='{{:prop}}'
                {{/props}} {{if ~isgrouped}}data-isgrouped="true"{{/if}} data-btnfaicon="traffic-light" data-disabled="true">
                </div>
              </div>
            {{/if}}
          </div>
          {{if #getIndex() < ~len-1}}
            <div class="row">
              <div class="col-sm-12">
                <hr/>
              </div>
            </div>
          {{/if}}
        ]]>
        </script>
        <script id="logTmpl" type="text/x-jsrender">
        <![CDATA[
          <div class="base">
            <form>
              <fieldset>
                <div class="flex">
                  <input class="form-control" type="text" data-link="flt" placeholder="filter"/>
                  <div class="checkbox hold">
                    <label><input type="checkbox" data-link="hold"/> Hold </label>
                  </div>
                  <select class="form-control end" data-link="end">
                    <option value="10">10</option>
                    <option value="50">50</option>
                    <option value="100">100</option>
                    <option value="9999">All</option>
                  </select>
                </div>
              </fieldset>
            </form>
            <ul>
              {^{if init}}
                <h6>Initialising</h6>
              {{else}}
                {^{for logs filter=~srcflt mapDepends='flt' srch='alias' start=0 end=end sorted=srtd}}
                  <li data-link="data-type{:type} class{:'log log_'+source+'_'+type+'_'+alias}">
                    <span data-link="class{:'logicon log_src_'+source+' log_typ_'+type}"></span>
                    <span class="logtitle">{^{>rawalias}}</span><span class="logtimestamp"> - {^{>~nicetime(timestamp)}}</span>
                    {^{if ~isset(arg)}}
                      <span class="logarg">
                        {^{if ~root.hold or ~root.flt}}
                          {^{:~jsonhighlight(~sanitize(arg, 250))}}
                        {{else}}
                          {^{:~sanitize(arg, 250)}}
                        {{/if}}
                      </span>
                    {{/if}}
                  </li>
                {{/for}}
              {{/if}}
            </ul>
          </div>
        ]]>
        </script>
        <script id="consoleTmpl" type="text/x-jsrender">
        <![CDATA[
          <div class="base" tabindex="0">
            <div>
              {^{for logs}}<div data-link="class{:'consoletype_'+console}"><span class="consoletimestamp">{^{>~nicetime(timestamp,true)}}</span>&nbsp;<span class="consolecomment">{^{>comment}}</span></div>{{/for}}
              <div><div class="consoleprompt">&gt;</div><div class="consoleinput" contenteditable="true" tabindex="0" spellcheck="false"></div></div>
            </div>
          </div>
        ]]>
        </script>
        <script id="serverlogTmpl" type="text/x-jsrender">
        <![CDATA[
          <div class="base">
            <div>
              {^{for logs}}
                <div data-link="class{:'consoletype_'+level}"><span class="consoletimestamp">{^{>~nicetime(timestamp,true)}}</span>&nbsp;<span class="consolecomment">[{^{>tag}}] {^{>message}}</span></div>
                {^{if error}}
                  <div data-link="class{:'consoletype_'+level+ ' consoledetail'}"><span class="consolecomment">{^{>error}}</span></div>
                {{/if}}
              {{/for}}
            </div>
          </div>
        ]]>
        </script>
        <script id="listTmpl" type="text/x-jsrender">
        <![CDATA[
          <div class="base">
            <form class="form-inline">
              <fieldset>
                <div class="form-group">
                  <input class="form-control nodelistfilter" type="text" data-link="flt" placeholder="filter"/>
                  <select class="form-control nodelistshow" data-link="end">
                    <option value="10">10</option>
                    <option value="20">20</option>
                    <option value="50">50</option>
                    <option value="100">100</option>
                    <option value="99999">All</option>
                  </select>
                </div>
                <p class="lsttotal">total: {^{>lst.length}}</p>
              </fieldset>
            </form>
            <div class="list-group list-group-basic">
              {^{for lst sort='node' end=end sorted=~flst}}
                <a class="list-group-item" data-link="href{:address} class{:~root^hosts[~encodr(host)].reachable ? 'list-group-item' : 'list-group-item unreachable'}"><img src="data:image/svg+xml;base64,{{:~root^hosts[~encodr(host)].icon}}"/>&nbsp;{^{:~highlight(name,~root.flt)}}</a>
              {{/for}}
              {^{if (~flst) && (end <= ~flst.length)}}
                <a class="list-group-item listmore">more</a>
              {{/if}}
            </div>
          </div>
        ]]>
        </script>
        <script id="localsTmpl" type="text/x-jsrender">
        <![CDATA[
          <div class="base">
            <form class="form-inline">
              <fieldset>
                <div class="form-group">
                  <input class="form-control localslistfilter" type="text" data-link="flt" placeholder="filter"/>
                  <select class="form-control localslistshow" data-link="end">
                    <option value="10">10</option>
                    <option value="20">20</option>
                    <option value="50">50</option>
                    <option value="100">100</option>
                    <option value="99999">All</option>
                  </select>
                </div>
                <p class="lsttotal">total: {^{>lst.length}}</p>
              </fieldset>
            </form>
            <div class="list-group list-group-basic">
              {^{for lst sort='node' end=end sorted=~flst}}
                <a class="list-group-item" data-link="href{:address} class{:~root^hosts[~encodr(host)].reachable ? 'list-group-item' : 'list-group-item unreachable'}"><img src="data:image/svg+xml;base64,{{:~root^hosts[~encodr(host)].icon}}"/>&nbsp;{^{:~highlight(name,~root.flt)}}</a>
              {{/for}}
              {^{if (~flst) && (end <= ~flst.length)}}
                <a class="list-group-item listmore">more</a>
              {{/if}}
            </div>
          </div>
        ]]>
        </script>        
        <script id="diagsTmpl" type="text/x-jsrender">
        <![CDATA[
          <div class="base">
            <table class="table">
              <tbody>
                <tr>
                  <th scope="row">Release</th>
                  <td>Open-source build <a href="/build.json"><strong id="build">{{>build.version}}</strong></a> by <a href="http://museumvictoria.com.au">Museum Victoria</a> &amp; <a href="http://automatic.com.au">Automatic Pty Ltd</a></td>
                </tr>
                <tr>
                  <th scope="row">Serving from</th>
                  <td>{{>hostname}}</td>
                </tr>
                <tr>
                  <th scope="row">Advertising</th>
                  <td>
                    {{for httpAddresses}}
                      {{>#data}}{{if #getIndex() < #parent.data.length -1}}<br/>{{/if}}
                    {{/for}}
                  </td>
                </tr>
                <tr>
                  <th scope="row">Uptime</th>
                  <td>{{>~fromtime(uptime)}}, start timestamp {{>~nicetime(startTime, false, 'llll')}}</td>
                </tr>
                <tr>
                  <th scope="row">Host path</th>
                  <td>{{>hostPath}}</td>
                </tr>
                <tr>
                  <th scope="row">Nodes root</th>
                  <td>{{>nodesRoot}}</td>
                </tr>
                <tr>
                  <th scope="row">Hosting rule</th>
                  <td>{{>hostingRule}}</td>
                </tr>
                <tr>
                  <th scope="row">Announcing agent</th>
                  <td>{{>agent}}</td>
                </tr>
                <tr>
                  <th scope="row">Build details</th>
                  <td>
                    Built {{>~nicetime(build.date, false, 'll')}} on {{>build.host}}<br/>
                    Origin <a href="#" data-link="href{:build.origin}">{{>build.origin}}</a><br/>
                    Branch <a href="#" data-link="href{:build.origin+'/tree/'+build.branch}">{{>build.branch}}</a>, last commit <a href="#" data-link="href{:build.origin+'/commit/'+build.id}">{{>build.id}}</a>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        ]]>
        </script>
      </body>
    </html>
  </xsl:template>
</xsl:stylesheet>
