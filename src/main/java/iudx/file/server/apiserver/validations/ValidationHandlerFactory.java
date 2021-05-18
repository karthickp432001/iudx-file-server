package iudx.file.server.apiserver.validations;


import static iudx.file.server.apiserver.utilities.Constants.HEADER_TOKEN;
import static iudx.file.server.apiserver.utilities.Constants.PARAM_END_TIME;
import static iudx.file.server.apiserver.utilities.Constants.PARAM_FILE_ID;
import static iudx.file.server.apiserver.utilities.Constants.PARAM_ID;
import static iudx.file.server.apiserver.utilities.Constants.PARAM_SAMPLE;
import static iudx.file.server.apiserver.utilities.Constants.PARAM_START_TIME;
import static iudx.file.server.apiserver.utilities.Constants.PARAM_TIME_REL;
import java.util.ArrayList;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import io.vertx.core.MultiMap;
import iudx.file.server.apiserver.validations.types.DateTypeValidator;
import iudx.file.server.apiserver.validations.types.FileIdTypeValidator;
import iudx.file.server.apiserver.validations.types.IDTypeValidator;
import iudx.file.server.apiserver.validations.types.SampleTypeValidator;
import iudx.file.server.apiserver.validations.types.TemporalRelTypeValidator;
import iudx.file.server.apiserver.validations.types.TokenTypeValidator;
import iudx.file.server.apiserver.validations.types.Validator;

public class ValidationHandlerFactory {

  private static final Logger LOGGER = LogManager.getLogger(ValidationHandlerFactory.class);

  public List<Validator> create(RequestType requestType, MultiMap parameters, MultiMap headers) {

    List<Validator> validator = null;
    switch (requestType) {
      case UPLOAD:
        validator = getUploadRequestValidations(parameters, headers);
        break;
      case DOWNLOAD:
        validator = getDownloadRequestValidations(parameters, headers);
        break;
      case DELETE:
        validator = getDeleteRequestValidations(parameters, headers);
        break;
      case QUERY:
        validator = getQueryRequestValidator(parameters, headers);
        break;
      default:
        break;
    }
    return validator;
  }


  private List<Validator> getUploadRequestValidations(final MultiMap parameters,
      final MultiMap headers) {
    List<Validator> validators = new ArrayList<>();

    validators.add(new IDTypeValidator(parameters.get(PARAM_ID), true));
    validators.add(new DateTypeValidator(parameters.get(PARAM_START_TIME), false));
    validators.add(new DateTypeValidator(parameters.get(PARAM_END_TIME), false));
    validators.add(new SampleTypeValidator(parameters.get(PARAM_SAMPLE), false));
    validators.add(new TokenTypeValidator(headers.get(HEADER_TOKEN), false));

    return validators;
  }

  private List<Validator> getDownloadRequestValidations(final MultiMap parameters,
      final MultiMap headers) {
    List<Validator> validators = new ArrayList<>();

    validators.add(new FileIdTypeValidator(parameters.get(PARAM_FILE_ID), true));
    validators.add(new TokenTypeValidator(headers.get(HEADER_TOKEN), false));

    return validators;
  }


  private List<Validator> getDeleteRequestValidations(final MultiMap parameters,
      final MultiMap headers) {
    List<Validator> validators = new ArrayList<>();

    validators.add(new FileIdTypeValidator(parameters.get(PARAM_FILE_ID), true));
    validators.add(new TokenTypeValidator(headers.get(HEADER_TOKEN), true));

    return validators;
  }


  private List<Validator> getQueryRequestValidator(final MultiMap parameters,
      final MultiMap headers) {
    List<Validator> validators = new ArrayList<>();

    validators.add(new IDTypeValidator(parameters.get(PARAM_ID), true));
    validators.add(new TemporalRelTypeValidator(parameters.get(PARAM_TIME_REL), true));
    validators.add(new DateTypeValidator(parameters.get(PARAM_START_TIME), true));
    validators.add(new DateTypeValidator(parameters.get(PARAM_END_TIME), false));

    return validators;

  }
}