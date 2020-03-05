/**
*******************************************************************************
This sample is designed for a set of images in which barcodes all locate in the
same region.
There are two modes to select:
1. Automatic region predetection mode if the region is indeterminate.
2. Manually region specification mode if the region is determinate.
Barcode region predetection enhances both reading efficiency and accuracy.
*******************************************************************************
*/

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/time.h>
#include "../../../include/DynamsoftBarcodeReader.h"
using  namespace  std;

int DecodeFile(CBarcodeReader& reader,const char* pszImageFile,int mode,int iLeft,int iTop,int iRight,int iBottom,float& fCostTime)
{
    struct timeval ullTimeBegin, ullTimeEnd;
    int iResult = 0;
    const char* pDefaultTemplate = "ALL_DEFAULT";
    const char* pDefaultTemplateWithRegion = "All_DEFAULT_WITHREGION";

	// Mode 1: Enable the region predetection mode to predetect barcode area automatically.
    char  tempDefaultTemplateJson[1024] = {0};
    snprintf(tempDefaultTemplateJson,1024,"{\"Version\": \"2.0\",\"ImageParameter\": {\"Name\": \"%s\",\"BarcodeFormatIds\": [\"BF_ALL\"],\"RegionPredetectionModes\": [{\"Mode\":\"RPM_GENERAL_RGB_CONTRAST\"}]}}",pDefaultTemplate);

	// Mode 2: Specify the barcode area manually.
    char tempTemplateJsonWithRegion[1024]={0};
    snprintf(tempTemplateJsonWithRegion,1024,"{\"Version\": \"2.0\",\"ImageParameter\": {\"Name\": \"%s\",\"BarcodeFormatIds\": [\"BF_ALL\"],\"RegionPredetectionModes\": [{\"Mode\":\"RPM_GENERAL\"}],\"RegionDefinitionNameArray\": [\"Region\"]},\"RegionDefinitionArray\": [{\"Name\": \"Region\",\"MeasuredByPercentage\": 1,\"Left\":%d,\"Top\":%d,\"Right\":%d,\"Bottom\":%d}]}",pDefaultTemplateWithRegion,iLeft,iTop,iRight,iBottom);


    char error[1024] ={0};
    if(mode == 0)
    {
    	iResult = reader.AppendTplStringToRuntimeSettings(tempDefaultTemplateJson,CM_OVERWRITE,error,1024);

    }else
    {
        iResult = reader.AppendTplStringToRuntimeSettings(tempTemplateJsonWithRegion,CM_OVERWRITE,error,1024);
    }

    if(iResult != DBR_OK)
	{
		printf("Error code: %d. Error message: %s\n", iResult, error);
		return iResult ;
	}
    gettimeofday(&ullTimeBegin, NULL);

    if(mode == 0)
    {
        iResult = reader.DecodeFile(pszImageFile,pDefaultTemplate);
    }else
    {
        iResult =  reader.DecodeFile(pszImageFile,pDefaultTemplateWithRegion);
    }

    gettimeofday(&ullTimeEnd, NULL);
    fCostTime = (float)((ullTimeEnd.tv_sec * 1000 * 1000 +  ullTimeEnd.tv_usec) - (ullTimeBegin.tv_sec * 1000 * 1000 + ullTimeBegin.tv_usec))/(1000 * 1000);
    return iResult;
}

int SelectMode(int& mode, int& iLeft, int& iTop, int& iRight, int& iBottom)
{
            int bExitFlag = 0;
            iLeft = -1;
            iTop = -1;
            iRight = -1;
            iBottom = -1;
            char pszBuffer[512] = {0};
            int iExitFlag = 0;
            int iLen = 0;
            while (true)
            {
                printf("Whether to set the detection area(Y:Yes/N:NO)?");
                fgets(pszBuffer,512,stdin);
                iLen = strlen(pszBuffer);
                if (iLen > 0)
                {
                    pszBuffer[iLen -1] ='\0';
                    if(strlen(pszBuffer) == 1 && (pszBuffer[0] == 'q' || pszBuffer[0] == 'Q'))
                    {
                        bExitFlag = 1;
                        return bExitFlag;
                    }
                    if(strlen(pszBuffer) == 1 && (pszBuffer[0] == 'n' || pszBuffer[0] == 'N'))
                    {
                        mode = 0;
                        return bExitFlag;
                    }
                    else if(strlen(pszBuffer) == 1 && (pszBuffer[0] == 'y' || pszBuffer[0] == 'Y'))
                    {
                        mode = 1;
                        while (true)
                        {
                            printf("Set left, top, right, bottom value (in percentage) of your region rectangle, e.g. 10,10,90,90:\r\n");
                            fgets(pszBuffer,512,stdin);
                            iLen = strlen(pszBuffer);
                            pszBuffer[iLen -1] ='\0';

                            if(strlen(pszBuffer) == 1 && (pszBuffer[0] == 'q' || pszBuffer[0] == 'Q'))
                            {
                                bExitFlag = 1;
                                break;
                            }

                            char* pPostion =NULL;
                            int  iPostion[4] = {-1,-1,-1,-1};
                            pPostion = strtok(pszBuffer,",");
                            int iIndex= 0;
                            while(pPostion!=NULL)
                            {
                                if(iIndex == 4)
                                {
                                    printf("Please input a vaild rect.");
                                }
                                try
                                {
                                    iPostion[iIndex] = atoi(pPostion);
                                }catch(...)
                                {
                                    printf("Please input a vaild rect.");
                                    break;
                                }
                                pPostion = strtok(NULL,",");
                                iIndex++;
                            }
                            if(iPostion[4]  == -1)
                            {
                                printf("Please input a vaild rect.");
                            }
                            iLeft = iPostion[0];
                            iTop = iPostion[1];
                            iRight = iPostion[2];
                            iBottom = iPostion[3];
                            break;
                        }
                        break;
                    }
                    else
                    {
                        printf("Please input a vaild value.");
                    }
                }
            }
            return bExitFlag;
        }

void ToHexString(unsigned char* pSrc, int iLen, char* pDest)
{
	const char HEXCHARS[16] = { '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F' };

	int i;
	char* ptr = pDest;

	for(i = 0; i < iLen; ++i)
	{
		snprintf(ptr, 4, "%c%c ", HEXCHARS[ ( pSrc[i] & 0xF0 ) >> 4 ], HEXCHARS[ ( pSrc[i] & 0x0F ) >> 0 ]);
		ptr += 3;
	}
}

void OutputResult(CBarcodeReader& reader,int errorcode,float time)
{
	char* pszTemp = NULL;
	char* pszTemp1 = NULL;
	char* pszTemp2 = NULL;
	int iRet = errorcode;
	pszTemp = (char*)malloc(4096);

	if (iRet != DBR_OK && iRet != DBRERR_MAXICODE_LICENSE_INVALID && iRet != DBRERR_LICENSE_EXPIRED && iRet != DBRERR_QR_LICENSE_INVALID && iRet != DBRERR_GS1_DATABAR_LICENSE_INVALID && iRet != DBRERR_PATCHCODE_LICENSE_INVALID &&
		iRet != DBRERR_1D_LICENSE_INVALID && iRet != DBRERR_PDF417_LICENSE_INVALID && iRet != DBRERR_DATAMATRIX_LICENSE_INVALID && iRet !=DBRERR_AZTEC_LICENSE_INVALID && iRet != DBRERR_GS1_COMPOSITE_LICENSE_INVALID )
	{
		snprintf(pszTemp, 4096, "Failed to read barcode: %s\r\n", CBarcodeReader::GetErrorString(iRet));
		printf(pszTemp);
		free(pszTemp);
		return;
	}

	TextResultArray *paryResult = NULL;
	reader.GetAllTextResults(&paryResult);

	if (paryResult->resultsCount == 0)
	{
		snprintf(pszTemp, 4096, "No barcode found. Total time spent: %.3f seconds.\r\n", time);
		printf(pszTemp);
		free(pszTemp);
		CBarcodeReader::FreeTextResults(&paryResult);
		return;
	}

	snprintf(pszTemp, 4096, "Total barcode(s) found: %d. Total time spent: %.3f seconds\r\n\r\n", paryResult->resultsCount, time);
	printf(pszTemp);
	for (int iIndex = 0; iIndex < paryResult->resultsCount; iIndex++)
	{
		snprintf(pszTemp, 4096, "Barcode %d:\r\n", iIndex + 1);
		printf(pszTemp);
		snprintf(pszTemp, 4096, "    Type: %s\r\n", paryResult->results[iIndex]->barcodeFormatString);
		printf(pszTemp);
		snprintf(pszTemp, 4096, "    Value: %s\r\n", paryResult->results[iIndex]->barcodeText);
		printf(pszTemp);
		pszTemp1 = (char*)malloc(paryResult->results[iIndex]->barcodeBytesLength*3 + 1);
		pszTemp2 = (char*)malloc(paryResult->results[iIndex]->barcodeBytesLength*3 + 100);
		ToHexString(paryResult->results[iIndex]->barcodeBytes, paryResult->results[iIndex]->barcodeBytesLength, pszTemp1);
		snprintf(pszTemp2, paryResult->results[iIndex]->barcodeBytesLength*3 + 100, "    Hex Data: %s\r\n", pszTemp1);
		printf(pszTemp2);
		free(pszTemp1);
		free(pszTemp2);
	}
	free(pszTemp);
	CBarcodeReader::FreeTextResults(&paryResult);
	return;
}

bool GetPath(char* pPath,bool bIfImagePath)
{
	char pszBuffer[512] = {0};
	int iExitFlag = 0;
	int iLen = 0;
	FILE* fp = NULL;
	while(1)
	{
		if(bIfImagePath)
		{
			printf("\r\n>> Step 1: Input your image file's full path:\r\n");
		}else
		{
			printf("\r\n>> Step 1: Input your template file's full path:\r\n");
		}

		fgets(pszBuffer,512,stdin);

		iLen = strlen(pszBuffer);

		if(iLen > 0)
		{
		   	pszBuffer[iLen -1] ='\0';

			if(strlen(pszBuffer) == 1 && (pszBuffer[0] == 'q' || pszBuffer[0] == 'Q'))
			{
				iExitFlag = 1;
				break;
			}

			memset(pPath, 0, 512);
			if (pszBuffer[0] == '\'')
			{
				if(pszBuffer[iLen - 3] == '\'')
					memcpy(pPath, &pszBuffer[1], iLen - 4);
				else if (pszBuffer[iLen - 2] == '\'')
					memcpy(pPath, &pszBuffer[1], iLen - 3);
			}
			else
				memcpy(pPath, pszBuffer, iLen);
			fp = fopen(pPath, "rb");
			if(fp != NULL)
			{
				fclose(fp);
				break;
			}
		}
		printf("Please input a valid path.\r\n");
	}
	return iExitFlag;
}






int main(int argc, const char* argv[])
{

	char pszImageFile[512] = {0};
	int iRet = -1;

	int iExitFlag = 0;
	float fCostTime = 0;

	printf("*************************************************\r\n");
	printf("Welcome to Dynamsoft Barcode Reader Demo\r\n");
	printf("*************************************************\r\n");
	printf("Hints: Please input 'Q'or 'q' to quit the application.\r\n");




	while(1)
	{
		CBarcodeReader reader;
		reader.InitLicense("t0068NQAAAGZgHJRTAQB/mGLCeSFiQjiiEIANotKUDNoTyr5Rx4EhspOQyNcuZCHsXcZqPUhgM3BRds6Uv6GTGodp2SZOPlM=");

        iExitFlag = GetPath(pszImageFile,true);
		if(iExitFlag)
			break;

        int iMode = 0;
        int iLeft = 0;
        int iTop = 0;
        int iRight = 0;
        int iBottom = 0;
        iExitFlag = SelectMode(iMode,iLeft,iTop,iRight,iBottom);
        if(iExitFlag)
            break;


		// Read barcode
        iRet = DecodeFile(reader,pszImageFile,iMode,iLeft,iTop,iRight,iBottom,fCostTime);
		// Output barcode result
		OutputResult(reader,iRet,fCostTime);
	}
	return 0;
}


