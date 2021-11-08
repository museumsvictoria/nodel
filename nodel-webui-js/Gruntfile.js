module.exports = function(grunt) {
  //Initializing the configuration object
  grunt.initConfig({
    pkg: grunt.file.readJSON('package.json'),
    googlefonts: {
      build: {
        options: {
          fontPath: './build/grunt/v1/fonts/',
          cssFile: './temp/googlefonts.css',
          httpPath: '../fonts/',
          formats: {woff: true, woff2: true},
          fonts: [
            {
              family: 'Roboto',
              styles: [
                100, 300, 400, 500, 700, 900
              ]
            },
            {
              family: 'Roboto Mono',
              styles: [
                100, 300, 400, 500, 700
              ]
            }
          ]
        }
      }
    },
    replace: {
      fontawesome: {
        src: ['./node_modules/@fortawesome/fontawesome-free/css/all.css'],
        dest: './node_modules/@fortawesome/fontawesome-free/css/all.local.css',
        replacements: [{
          from: 'webfonts',
          to: 'fonts'
        }]
      }
    },
    twbs: {
      dark: {
        options: {
          bootstrap: './node_modules/bootstrap',
          less: './src/dark/'
        }
      },
      light: {
        options: {
          bootstrap: './node_modules/bootstrap',
          less: './src/light/'
        }
      }
    },
    copy: {
      updatetheme: {
        files: [
          {
            src: 'src/theme.less',
            dest: 'src/light/theme.less'
          },
          {
            src: 'src/theme.less',
            dest: 'src/dark/theme.less'
          }
        ]
      },
      main: {
        files: [
          {
            expand: true,
            cwd: './node_modules/@fortawesome/fontawesome-free/webfonts/',
            src: '**',
            dest: 'build/grunt/v1/fonts/',
            flatten: true, 
            filter: 'isFile'
          },
          {
            expand: true,
            cwd: './node_modules/bootstrap/dist/fonts/',
            src: '**',
            dest: 'build/grunt/v1/fonts/',
            flatten: true, 
            filter: 'isFile'
          },
          {
            src: 'src/custom.css',
            dest: 'build/grunt/css/custom-sample.css'
          },
          {
            src: 'src/nodel.js',
            dest: 'build/grunt/v1/js/nodel.js'
          },
          {
            src: 'src/custom.js',
            dest: 'build/grunt/js/custom-sample.js'
          },
          {
            src: 'src/index.xml',
            dest: 'build/grunt/index-sample.xml'
          },
          {
            src: 'src/index.xsd',
            dest: 'build/grunt/v1/index.xsd'
          },
          {
            src: 'src/index.xsl',
            dest: 'build/grunt/v1/index.xsl'
          },
          {
            src: 'src/index.htm',
            dest: 'build/grunt/index.htm'
          },
          {
            src: 'src/index.smil',
            dest: 'build/grunt/index-sample.smil'
          },
          {
            src: 'src/templates.xsl',
            dest: 'build/grunt/v1/templates.xsl'
          },
          {
            src: 'src/status.xml',
            dest: 'build/grunt/status-sample.xml'
          },
          {
            src: 'src/nodel.xml',
            dest: 'build/grunt/nodel.xml'
          },
          {
            src: 'src/nodes.xml',
            dest: 'build/grunt/nodes.xml'
          },
          {
            src: 'src/locals.xml',
            dest: 'build/grunt/locals.xml'
          },
          {
            src: 'src/toolkit.xml',
            dest: 'build/grunt/toolkit.xml'
          },
          {
            src: 'src/diagnostics.xml',
            dest: 'build/grunt/diagnostics.xml'
          },
          {
            src: 'src/schemas.json',
            dest: 'build/grunt/schemas.json'
          },
          {
            src: 'src/logo.png',
            dest: 'build/grunt/v1/img/logo.png'
          },
          {
            src: 'src/custom-sample.py',
            dest: 'build/grunt/custom-sample.py'
          },
          {
            src: 'src/favicon.ico',
            dest: 'build/grunt/v1/img/favicon.ico'
          }
        ]
      },
      deploy: {
        files: [
          {
            expand: true,
            cwd: 'build/grunt/',
            src: '**',
            dest: '/Local/Nodel/custom/content/'
          }
        ]
      }
    },
    concat_css: {
      options: {},
      dark: {
        src: [
          './node_modules/@fortawesome/fontawesome-free/css/all.local.css',
          './node_modules/bootstrap/dist/css/bootstrap.css',
          './node_modules/bootstrap/dist/css/bootstrap-theme.css',
          './node_modules/jquery.scrollbar/jquery.scrollbar.css',
          './node_modules/codemirror/lib/codemirror.css',
          './node_modules/codemirror/addon/dialog/dialog.css',
          './temp/googlefonts.css'
        ],
        dest: './build/grunt/v1/css/components.css'
      },
      light: {
        src: [
          './node_modules/@fortawesome/fontawesome-free/css/all.local.css',
          './node_modules/bootstrap/dist/css/bootstrap.css',
          './node_modules/bootstrap/dist/css/bootstrap-theme.css',
          './node_modules/jquery.scrollbar/jquery.scrollbar.css',
          './node_modules/codemirror/lib/codemirror.css',
          './node_modules/codemirror/addon/dialog/dialog.css',
          './temp/googlefonts.css'
        ],
        dest: './build/grunt/v1/css/components.default.css'
      }
    },
    concat: {
      options: {
        separator: ';'
      },
      js_main: {
        src: [
          './node_modules/jquery/dist/jquery.js',
          './node_modules/jsviews/jsviews.js',
          './node_modules/bootstrap/dist/js/bootstrap.js',
          './node_modules/moment/moment.js',
          './node_modules/jquery.scrollbar/jquery.scrollbar.js',
          './node_modules/pagedown/Markdown.Converter.js',
          './node_modules/xregexp/xregexp-all.js',
          './node_modules/gsap/umd/CSSPlugin.js',
          './node_modules/gsap/umd/EasePack.js',
          './node_modules/gsap/umd/TweenLite.js',
          './node_modules/gsap/umd/jquery.gsap.js',
          './node_modules/fuzzyset.js/lib/fuzzyset.js',
          './node_modules/codemirror/lib/codemirror.js',
          './node_modules/codemirror/addon/display/autorefresh.js',
          './node_modules/codemirror/addon/edit/matchbrackets.js',
          './node_modules/codemirror/addon/search/searchcursor.js',
          './node_modules/codemirror/addon/search/search.js',
          './node_modules/codemirror/addon/dialog/dialog.js',
          './node_modules/codemirror/addon/search/jump-to-line.js',
          './node_modules/codemirror/mode/python/python.js',
          './node_modules/codemirror/mode/javascript/javascript.js',
          './node_modules/codemirror/mode/xml/xml.js',
          './node_modules/codemirror/mode/css/css.js',
          './node_modules/codemirror/mode/clike/clike.js',
          './node_modules/codemirror/mode/groovy/groovy.js',
          './node_modules/codemirror/mode/sql/sql.js',
          './node_modules/codemirror/mode/shell/shell.js',
          './node_modules/cm-resize/dist/cm-resize.js',
          './node_modules/array.findindex/src/array-find-index-polyfill.js',
          './node_modules/identicon.js/identicon.js',
          './node_modules/xxhashjs/build/xxhash.js',
          './node_modules/google-charts/dist/googleCharts.js',
          './node_modules/lodash/lodash.js',
          './src/polyfill.js'
        ],
        dest: './build/grunt/v1/js/components.js'
      }
    },
    uglify: {  
      options: {  
        compress: true  
      },  
      applib: {  
        src: './build/grunt/v1/js/components.js',
        dest: './build/grunt/v1/js/components.min.js'  
      }
    }
  });
  // Plugin loading
  grunt.loadNpmTasks('grunt-contrib-concat');
  grunt.loadNpmTasks('grunt-contrib-copy');
  grunt.loadNpmTasks('grunt-concat-css');
  grunt.loadNpmTasks('grunt-contrib-uglify');
  grunt.loadNpmTasks('grunt-twbs');
  grunt.loadNpmTasks('grunt-text-replace');
  grunt.loadNpmTasks('grunt-google-fonts');
  // Task definition
  grunt.registerTask('default', ['googlefonts', 'copy:updatetheme','replace','twbs:dark','concat_css:dark','twbs:light','concat_css:light','copy:main','concat','uglify']);
  grunt.registerTask('build', ['copy:updatetheme','replace','twbs:dark','concat_css:dark','twbs:light','concat_css:light','copy:main','concat','uglify']);
  grunt.registerTask('gfonts', ['googlefonts']);
  grunt.registerTask('deploy', ['copy:main','copy:deploy']);
};
