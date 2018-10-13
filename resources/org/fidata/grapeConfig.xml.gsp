<?xml version="1.0" encoding="UTF-8"?>
<ivy-settings>
    <!--Authentication required for publishing (deployment). 'Artifactory Realm' is the realm used by Artifactory so don't change it.-->
    <credentials host="fidata.jfrog.io" realm="Artifactory Realm" username="$username" passwd="$password" />
    <resolvers>
        <chain name="downloadGrapes" returnFirst="true">
            <filesystem name="cachedGrapes">
                <ivy pattern="\${user.home}/.groovy/grapes/[organisation]/[module]/ivy-[revision].xml" />
                <artifact pattern="\${user.home}/.groovy/grapes/[organisation]/[module]/[type]s/[artifact]-[revision](-[classifier]).[ext]" />
            </filesystem>
            <ibiblio name="public" m2compatible="true" root="https://fidata.jfrog.io/fidata/libs-release/" />
            <ibiblio name="localm2" root="file:\${user.home}/.m2/repository/" checkmodified="true" changingPattern=".*" changingMatcher="regexp" m2compatible="true" />
        </chain>
    </resolvers>
</ivy-settings>
