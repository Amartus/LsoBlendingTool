# SonataBlendingTool
A  project to support  a consistent code-base generation from MEF API definitions and product specifications 

# Project build

Project build with Maven 

```shell script
mvn clean package
```

# Project run (CLI)
```shell script
java -jar blender-1.0.jar <commang> [args]
```

## Tool generate command synopis 

```shell script
NAME
        sonata-blending-tool-cli generate - Generate code using configuration.

SYNOPSIS
        sonata-blending-tool-cli generate
                (-c <configuration file> | --config <configuration file>)
                [(-e <files encoding> | -encoding <files encoding>)]
                (-i <spec file> | --input-spec <spec file>)
                [(-m <model to be augmented> | -model-name <model to be augmented>)]
                [(-p <product specifications> | --product-spec <product specifications>)...]

OPTIONS
        -c <configuration file>, --config <configuration file>
            Path to configuration file configuration file. It can be json or
            yaml.If file is json, the content should have the format
            {"optionKey":"optionValue", "optionKey1":"optionValue1"...}.If file
            is yaml, the content should have the format optionKey:
            optionValueSupported options can be different for each language. Run
            config-help -g {generator name} command for language specific config
            options.

        -e <files encoding>, -encoding <files encoding>
            encoding used to read API and product definitions. By default system
            encoding is used

        -i <spec file>, --input-spec <spec file>
            location of the OpenAPI spec, as URL or file (required)

        -m <model to be augmented>, -model-name <model to be augmented>
            Model which will be hosting product specific extensions (E.g.
            ProductCharacteristics)

        -p <product specifications>, --product-spec <product specifications>
            sets of product specification you would like to integrate



```