# SonataBlendingTool

A project to support a consistent code-base generation from MEF API definitions and product specifications

# Project build

Project build with Maven

```shell script
mvn clean package
```

# Project run (CLI)

Project requires Java runtime in version 11 or greater.

```shell script
java -jar blender-all-in.jar <command> [args]
```

## Tool `generate` command synopsis

```shell script
NAME
        sonata-blending-tool-cli generate - Generate code using configuration.

SYNOPSIS
        sonata-blending-tool-cli generate
                [ {-b | --blending-schema} <specifications to be blend (integrate) in>... ]
                [ {-c | --config} <configuration file> ]
                [ {-d | --spec-root-dir} <root directory for specificatins to be blended> ]
                [ {-e | -encoding} <files encoding> ]
                [ {-i | --input-spec} <spec file> ]
                [ {-m | --model-name} <model to be augmented> ]
                [ --strict-mode ]

OPTIONS
        -b <specifications to be blend (integrate) in>, --blending-schema
        <specifications to be blend (integrate) in>
            sets of specifications (e.g. specific product or service
            definitions) you would like to integrate

            This option is part of the group 'allOrSelective' from which only
            one option may be specified


        -c <configuration file>, --config <configuration file>
            Path to configuration file configuration file. It can be json or
            yaml.If file is json, the content should have the format
            {"optionKey":"optionValue", "optionKey1":"optionValue1"...}.If file
            is yaml, the content should have the format optionKey:
            optionValueSupported options can be different for each language.
            Run config-help -g {generator name} command for language specific
            config options.

            This option may occur a maximum of 1 times


        -d <root directory for specificatins to be blended>, --spec-root-dir
        <root directory for specificatins to be blended>
            sets of product specification root directory for specifications you
            would like to integrate

            This option may occur a maximum of 1 times


        -e <files encoding>, -encoding <files encoding>
            encoding used to read API and product definitions. By default
            system encoding is used

        -i <spec file>, --input-spec <spec file>
            location of the OpenAPI spec, as URL or file (required)

            This option may occur a maximum of 1 times


        -m <model to be augmented>, --model-name <model to be augmented>
            Model which will be hosting product specific extensions (e.g.
            MEFProductConfiguration)


        --strict-mode
            Verify that model to be augmented allows for extension (contains
            discriminator definition).
            If strict-mode is `false` tool will add a discriminator on the fly
            if possible.
```

### Usage example

Assuming you have a valid spring generator configuration
(as explained [here](https://openapi-generator.tech/docs/generators/spring)) in `configurations/spring`

```shell script
java -jar blender-all-in.jar generate -i .\productApi\serviceability\offeringQualification\productOfferingQualificationManagement.api.yaml \
     -c ./configurations/spring/spring-server.yaml \
     -d .\productSchema\carrierEthernet \ 
     -b accessEline\accessElineOvc.yaml -b carrierEthernetOperatorUni\carrierEthernetOperatorUni.yaml
```

## Tool `blend` command synopsis

Blend command generates an OAS 3 definition of combined API and product specifications

```shell script
NAME
        sonata-blending-tool-cli blend - Blend Product / Service Specifications
        into OpenAPI.

SYNOPSIS
        sonata-blending-tool-cli blend
                [ {-b | --blending-schema} <specifications to be blend (integrate) in>... ]
                [ {-d | --spec-root-dir} <root directory for specifications to be blended> ]
                [ {-discover | --auto-discover} ]
                [ {-e | -encoding} <files encoding> ]
                [ {-f | --force-override} ] [ {-i | --input-spec} <spec file> ]
                [ {-m | --model-name} <model to be augmented> ]
                [ --no-resolve-external ]
                [ {-o | --output} <Output file name> ]
                [ --path-security <pathSecurity> ] [ --sorted ]
                [ --strict-mode ] [ --validate ]

OPTIONS
        -b <specifications to be blend (integrate) in>, --blending-schema
        <specifications to be blend (integrate) in>
            sets of specifications (e.g. specific product or service
            definitions) you would like to integrate

            This option is part of the group 'allOrSelective' from which only
            one option may be specified

        -d <root directory for specifications to be blended>, --spec-root-dir
        <root directory for specifications to be blended>
            root directory for specifications.

            This option may occur a maximum of 1 times

        -discover, --auto-discover
            Try to use a parent type name from x-mef-target extension in
            schema. If not defined the fallback is to take 'model-name'

        -e <files encoding>, -encoding <files encoding>
            encoding used to read API and product definitions. By default
            system encoding is used

        -f, --force-override
            Override output if exist

            This option may occur a maximum of 1 times


        -i <spec file>, --input-spec <spec file>
            location of the OpenAPI spec, as URL or file (required)

            This option may occur a maximum of 1 times


        -m <model to be augmented>, --model-name <model to be augmented>
            Model which will be hosting product specific extensions (e.g.
            MEFProductConfiguration)

        --no-resolve-external
            Do not resolve external references. By default external references
            are resolved.

            This option may occur a maximum of 1 times


        -o <Output file name>, --output <Output file name>
            Output file name. Throws exception if file exists. If it is not
            provided output file is 'output-spec'.modified

            This option may occur a maximum of 1 times

        --path-security <pathSecurity>
            mechanism to use to secure API paths. default disabled

            This options value is restricted to the following set of values:
                oauth2
                oauth2_simple
                disabled

        --sorted
            sort data types in a lexical order

            This option may occur a maximum of 1 times


        --strict-mode
            Verify that model to be augmented allows for extension (contains
            discriminator definition).
            If strict-mode is `false` tool will add a discriminator on the fly
            if possible.

        --validate
            Validate consistency of OAS definition with its 3.0.x json schema
            definition

            This option may occur a maximum of 1 times
```


### Usage example for Sonata

Assumption is that this command is run from root of the Sonata SDK directory and jar file is in the same directory.

```shell script
java -jar blender-all-in.jar blend -i .\productApi\serviceability\offeringQualification\productOfferingQualificationManagement.api.yaml \
     -d .\productSchema\carrierEthernet \ 
     -b accessEline\accessElineOvc.yaml \ 
     -b carrierEthernetOperatorUni\carrierEthernetOperatorUni.yaml
```

### Usage example for Legato

Assumption is that this command is run from root of the Legato SDK directory and jar file is in the same directory.

```shell script
 java -jar blender-all-in.jar blend -d spec/legato/carrierEthernet -m MefServiceConfiguration  \
     -i ./api/legato/serviceProvisioning/serviceOrdering/v4/serviceOrderingApi.openapi.yaml \ 
    -b carrierEthernetOvc.yaml \ 
    -b carrierEthernetSubscriberUni.yaml
```

### Additional features


#### Auto-discover mode
If a schema to blend defines `x-mef-target` extension it can be used as a target class name for given type.
To enable this feature please use `-discover` flag. In auto-discover mode if `x-mef-target` is not defined for the schema
the `model-name` will be used as a fallback.

## Tool `merge` command synopsis

Merge command generates an OAS 3 definition that is a minimal OAS file including `components/schemas` only
with schema definition for all selected `blending-schema`s.

```shell script
SYNOPSIS
        sonata-blending-tool-cli merge
                [ {-b | --blending-schema} <specifications to be blend (integrate) in>... ]
                [ {-d | --spec-root-dir} <root directory for specifications> ]
                [ {-discover | --auto-discover} ]
                [ {-f | --force-override} ]
                [ {-m | --model-name} <model to be augmented> ]
                {-o | --output} <Output file name> [ --sorted ]

OPTIONS
        -b <specifications to be blend (integrate) in>, --blending-schema
        <specifications to be blend (integrate) in>
            sets of specifications (e.g. specific product or service
            definitions) you would like to integrate

            This option is part of the group 'allOrSelective' from which only
            one option may be specified


        -d <root directory for specifications>, --spec-root-dir <root directory for specifications>


            This option may occur a maximum of 1 times

        -discover, --auto-discover
            Try to use a parent type name from x-mef-target extension in
            schema. If not defined the fallback is to take 'model-name'
            
        -f, --force-override


            This option may occur a maximum of 1 times


        -m <model to be augmented>, --model-name <model to be augmented>
            Model which will be hosting product specific extensions (e.g.
            MEFProductConfiguration)

        -o <Output file name>, --output <Output file name>
            Output file name. Throws exception if file exists.

            This option may occur a maximum of 1 times


        --sorted
            sort data types in a lexical order

            This option may occur a maximum of 1 times
```

### Usage example for Sonata

Assumption is that this command is run from any directory. `-d` parameter points to root of the schema directory.
In this example this directory hosts

```shell script
java -jar blender-all-in.jar merge \
     -d ${rootSchemaDirectory} \ 
     -b accessEline\accessElineOvc.yaml \ 
     -b carrierEthernetOperatorUni\carrierEthernetOperatorUni.yaml
```

### Usage example for Legato

Assumption is that this command is run from the schema directory. You could also use `-d` parameter to point to
different directory as in the example above.

```shell script
java -jar blender-all-in.jar merge \
    -b carrierEthernetOvc.yaml \ 
    -b carrierEthernetSubscriberUni.yaml
```