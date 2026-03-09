#!/bin/bash
#set -x
declare -a DTX_JARGS
LIBDIR=lib
export CYGWIN=nodosfilewarning
test -z "${ABASE}" && ABASE=`dirname $0`
if test ! -d "${ABASE}/$LIBDIR"; then
  # Run a Spring jar...
  TABASE="$ABASE/../../../target/libs"
  if test -d "$TABASE"; then
    JBASE="$TABASE"
    LIBDIR=`basename "$TABASE"`
    ABASE=`dirname "$JBASE"`
    rm "$JBASE"/*-plain.jar 2>/dev/null
    JAR=`echo "$JBASE"/*.jar`
  else
    JAR=`echo "$ABASE"/*.jar`
  fi
fi
OABASE=$ABASE
test -z "${TARGET}" && TARGET=${OABASE}/target
USERINIT=${TARGET}/init.sh && test -f "${USERINIT}" && source "${USERINIT}"
KEYTOOL="${KEYTOOL:-keytool}"
test -z "${DTX_JAVA_EXECUTABLE}" && test ! -z "${JAVA_HOME}" && DTX_JAVA_EXECUTABLE="${JAVA_HOME}/bin/java" && KEYTOOL="${JAVA_HOME}/bin/keytool"
test ! -z "${DTX_JAVA_EXECUTABLE}" && KEYTOOL=`dirname ${DTX_JAVA_EXECUTABLE}`/keytool
DTX_JAVA_EXECUTABLE="${DTX_JAVA_EXECUTABLE:-java}"
DTX_ENV=${DTX_ENV:-true}
test -z "${DTX_ENV_HOME}" -o -f ${DTX_ENV_HOME}/.update-ignore && DTX_ENV=false
$DTX_ENV || DTX_ENV_HOME=
UBASE=${USERPROFILE:-$HOME}/.detox-utils; mkdir "$UBASE" 2>/dev/null
UBASE=`realpath ${UBASE}`
test -z "$JBASE" && JBASE="$ABASE/$LIBDIR"
ASPECTJ=aspectjweaver*.jar && ASPECTJ=`echo $ASPECTJ`
COLUMNS=$(tput cols)
MACOSX=false
unameOut="$(uname -s)"
case "${unameOut}" in
    Linux*)     machine=Linux;;
    Darwin*)    machine=Mac;;
    MINGW|CYGWIN*)    machine=Min;;
    MSYS_NT*)   machine=Min;;
    *)          machine="UNKNOWN:${unameOut}"
esac
[ "$machine" == "Mac" ] && MACOSX=true && export DYLD_LIBRARY_PATH=$JBASE/native:$DYLD_LIBRARY_PATH
CYGWINBOOL=false
if [ "$machine" == "Min" ];then
	CYGWINBOOL=true
	ADTX_JAVA_EXECUTABLE=`which "$DTX_JAVA_EXECUTABLE" 2>/dev/null` && DTX_JAVA_EXECUTABLE=`cygpath -a "$ADTX_JAVA_EXECUTABLE"` || DTX_JAVA_EXECUTABLE=`cygpath -a "$DTX_JAVA_EXECUTABLE"`
fi
$CYGWINBOOL && export PATH="$JBASE/native:$PATH"
DTX_CLASSPATH=$UBASE/$LIBDIR/cp:$JBASE/cp:$DTX_CLASSPATH
test -z "$JAR" && DTX_CLASSPATH=$DTX_CLASSPATH:$(echo "$JBASE"/*.jar | tr ' ' ':')
if test ! -e $DTX_JAVA_EXECUTABLE; then
	DTX_JAVA_EXECUTABLE=$DTX_JAVA_EXECUTABLE.exe
	test -e $DTX_JAVA_EXECUTABLE && CYGWINBOOL=true
fi
JARCH=32

eval `$DTX_JAVA_EXECUTABLE -version 2>&1| dos2unix |awk -F ' ' '/version/ {split($3,a,/["\.]/);print "JAVAVER="a[2]a[3]} /HotSpot/ {sub(/-Bit/, "", $3);print "JARCH="$3}'`
BASE_NAME=`basename -s .sh $0 2>/dev/null` || BASE_NAME=`basename $0 .sh 2>/dev/null`
if [ -z $DTX_MAIN_CLASS ]; then
	DTX_MAIN_CLASS=$BASE_NAME
	[[ $DTX_MAIN_CLASS == startup ]] && DTX_MAIN_CLASS=Main
	DTX_MAIN_CLASS=hu.detox.${DTX_MAIN_CLASS}
fi
argread() {
	local ARGD1="-args-${JARCH}.txt"
	local ARGD2="-args.txt"
	local JARGSF
	for f in $ABASE ${TARGET} ${DTX_ENV_HOME} ${UBASE}; do
		for r in $*; do
			for x in $f/$r$ARGD1 $f/$r$ARGD2; do
				if test -f "$x"; then
					JARGSF=${x}.bak
					test ! -f ${JARGSF} && dos2unix -n $x ${JARGSF}
					while read; do DTX_JARGS+=("$REPLY"); done <${JARGSF}
				fi
			done
		done
	done
}
DEBUG=${DEBUG:-false}
[[ ${DEBUG} == *"local"* ]] && DTX_DJARGS="$DTX_DJARGS -Ddebug=true -Daj.weaving.verbose=true"
[[ ${DEBUG} == *"remote"* ]] && DTX_DJARGS="$DTX_DJARGS -Xdebug -Xnoagent -Djava.compiler=NONE -Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=5005"
[[ ${DEBUG} == *"mgmtr"* ]] && DTX_DJARGS="$DTX_DJARGS -Dcom.sun.management.jmxremote -Dcom.sun.management.jmxremote.authenticate=false -Dcom.sun.management.jmxremote.port=5006 -Dcom.sun.management.jmxremote.ssl=false"
[[ ${DEBUG} == *"mgmtl"* ]] && DTX_DJARGS="$DTX_DJARGS -Dcom.sun.management.jmxremote -Dcom.sun.management.jmxremote.local.only=true"
[[ ${DEBUG} == *"covc"* ]] && DTX_DJARGS="$DTX_DJARGS -javaagent:JBASE/jacocoagent.jar=destfile=%TARGET%/%DTX_EXECUTABLE%.exec,append=true"
[[ ${DEBUG} == *"covf"* ]] && DTX_DJARGS="$DTX_DJARGS -javaagent:JBASE/jacocoagent.jar=destfile=%TARGET%/%DTX_EXECUTABLE%.exec,append=false,jmx=true"
DTX_JARGS+=("-Dconsole_width=$COLUMNS" "-splash:res/splash.jpg")
USERINIT=${UBASE}/init.sh && test -f "${USERINIT}" && source "${USERINIT}"
test -z "${DTX_ENV_HOME}" && DTX_ENV=false || DTX_ENV=true
$DTX_ENV && USERINIT=${DTX_ENV_HOME}/init.sh && test -f "${USERINIT}" && source "${USERINIT}"
USERINIT=${UBASE}/${BASE_NAME}.sh && test -f "${USERINIT}" && source "${USERINIT}"
$DTX_ENV && USERINIT=${DTX_ENV_HOME}/${BASE_NAME}.sh && test -f "${USERINIT}" && source "${USERINIT}"
argread java ${BASE_NAME}
test -f "$ASPECTJ" || ASPECTJ=$JBASE/aspectjweaver*.jar && ASPECTJ=`echo $ASPECTJ`
test -f "$ASPECTJ" || ASPECTJ=
test -z "${STDIN}" && STDIN=`basename $0` && [[ "$DTX_JAVA_EXECUTABLE" == *javaw* ]] && STDIN=-
test -z "${UPDATE}" && UPDATE=$TARGET/update
if $CYGWINBOOL; then
	ABASE=`cygpath -w -a $ABASE`
	DTX_CLASSPATH=`cygpath -w -a -p $DTX_CLASSPATH 2>/dev/null`
	test ! -z "$ASPECTJ" && ASPECTJ=`find $JBASE | grep aspectjweaver` && ASPECTJ=`cygpath -w $ASPECTJ`
else
	export LD_LIBRARY_PATH=$JBASE/native:$LD_LIBRARY_PATH
fi
test ! -z "$ASPECTJ" && DTX_JARGS+=("-javaagent$ASPECTJ")
if [ -e $UPDATE ]; then
	if [[ `basename $UPDATE` == update ]]; then
		cp -r $UPDATE/* $OABASE/
		UPF="$OABASE/delete.txt"
		dos2unix "$UPF"
		while read; do rm -r "$OABASE/$REPLY"; done <"$UPF"
		rm "$UPF"
		rm -r "$UPDATE"
	else
		"$DTX_JAVA_EXECUTABLE" "${DTX_JARGS[@]}" "-Dbase=$ABASE" "-Dtarget=$TARGET" -cp "$DTX_CLASSPATH" hu.detox.launcher.Main
	fi
fi
if test -z "$JAR"; then
  DTX_JARGS+=("$DTX_MAIN_CLASS")
else
  DTX_JARGS+=("-Dloader.main=$DTX_MAIN_CLASS" "-jar" "$JAR")
fi
$DTX_SHELL "$DTX_JAVA_EXECUTABLE" -DstdIn=$STDIN "-Dtarget=$TARGET" "-Dbase=$ABASE" -cp "$DTX_CLASSPATH" "${DTX_JARGS[@]}" "$@"
